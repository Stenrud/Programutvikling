package controller;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import model.*;
import model.Parser.PatternParser;
import model.rules.RuleFormatException;
import model.rules.RuleParser;
import s305080.Gif.GifSaver;
import s305080.PatternSaver.ToFile;
import tools.MessageBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The controller that handles everything that happens on the canvas.
 * mouse control
 * zoom control
 * grid positioning
 * Draw gameboard on canvas
 * animation
 * keyboard handeling
 * animation speed
 * save to gif
 */
public class CanvasController {

    private MasterController masterController;

    //region mouse input

    // Is used to calculate the distance the mouse has traveled since last MouseDragEvent.
    private int currMousePosX;
    private int currMousePosY;
    private int prevMousePosX;
    private int prevMousePosY;

    private boolean mouseDrag;
    private boolean mouseOnCanvas;

    //endregion

    //region visuals

    @FXML private Canvas canvas;
    private GraphicsContext gc;

    private Cell cell;
    private CameraView cView = new CameraView();

    // whether or not to draw grid lines
    private boolean gridLines;
    private boolean userWantsGridLines;

    //endregion

    //region animation

    private Thread thread;

    private AnimationTimer animationTimer;
    private long timer;

    // controls the framerate
    private int frameDelay;
    private boolean running = true;
    private boolean interaction = false;
    //endregion

    //region game

    private GameOfLife gol;

    // holds pattern to be imported
    private boolean[][] clipBoardPattern;

    private boolean importing = false;

    //endregion

    //region s305080

    // bounding box for selected area
    private double [] markup;

    //list of buttons being pressed right now
    private List<String> buttonsPressed;
    private int times = 1;

    //endregion

    /**
     * Sets master controller, creates an instance of the StaticGameOfLife object and prepares the grid and gets the Graphics Content of the canvas.
     * Also it sets up listeners and prepare the animation. Final it launches the animation.
     * @param masterController The controller that holds the entire program together
     */
    public void initialize(MasterController masterController) {

        this.masterController = masterController;

        initializeGameParameters();

        gol = new DynamicGameOfLife();

        // displays the rule in the toolbar
        masterController.getToolController().setRuleLabel(gol.getRule());
        gc = canvas.getGraphicsContext2D();

        // to store buttons being pressed
        buttonsPressed = new ArrayList<>();

        cView.updateView(gol, cell.getSize(), (int)canvas.getWidth(), (int)canvas.getHeight());

        initializeListeners();
        initializeAnimation();
        checkIfShouldStillDrawGrid();
        masterController.getToolController().setZoom(cell.getSize());
        startAnimation();
    }

    /**
     * Gets the game parameters from the config file
     */
    private void initializeGameParameters() {

        // creates new instance of Cell
        cell = new Cell(masterController.getConfig());

        // gets parameters from config file
        frameDelay = masterController.getConfiguration().getGameSpeed();
        userWantsGridLines = masterController.getConfiguration().isGridLinesOn();
        masterController.getToolController().setSpeed(frameDelay);
    }

    /**
     * Initializes the animation. The "timer" and "frameDelay" are used for having dynamic frame rate.
     */
    private void initializeAnimation() {

        thread = new Thread();
        animationTimer = new AnimationTimer() {

            @Override
            public void handle(long now) {

                //To control game speed
                if (now / 1000000 - timer > frameDelay) {

                    if(!interaction) {

                        // to be able to pan the board without lag during large simulations, nextGeneration()
                        // is put inside a thread
                        if(!thread.isAlive()){
                            thread = new Thread(() -> {
                                for (int i = 0; i < times; i++) {
                                    gol.nextGeneration();
                                }
                                Platform.runLater(() -> renderCanvas());
                            });
                            thread.start();
                        }

                        timer = now / 1000000;

                        giveCellCount();
                    }
                }
            }
        };
    }

    /**
     * Replaces the existing dynamic game of life board with a static one
     */
    void changeToStatic(){
        // creates new static grid
        GameOfLife newGol = new StaticGameOfLife(1500, 1500, gol.getRule().toString());

        // sets it as primary gol
        changeGol(newGol);
    }

    /**
     * Replaces the existing static game of life board with a dynamic one
     */
    void changeToDynamic(){
        // creates new dynamic gol
        GameOfLife newGol = new DynamicGameOfLife(gol.getRule().toString());
        //sets it as primary gol
        changeGol(newGol);
    }

    /**
     * Does necessary calculations to set newGol in the position as primary gol
     * @param newGol the GameOfLife object to set as primary gol
     */
    private void changeGol(GameOfLife newGol) {

        // inserts the old pattern
        insertOldGrid(gol, newGol, gol.getGridWidth(), gol.getGridHeight());

        // sets the same offset as before
        cView.boardOffsetX = cView.getCommonOffsetX(gol, cell.getSize());
        cView.boardOffsetY = cView.getCommonOffsetY(gol, cell.getSize());

        // sets the same rule as before
        newGol.setRule(gol.getRule().toString());

        gol = newGol;
        renderCanvasIfLowFPS();
    }

    /**
     * Moves pattern with dimensions widthAndHeight from gol to newGol
     * @param gol board with pattern
     * @param newGol board without pattern
     * @param widthAndHeight dimensions of pattern to be moved, usualy the size of the smallest GameOfLife
     */
    private void insertOldGrid(GameOfLife gol, GameOfLife newGol, int ... widthAndHeight) {

        waitForThread();
        boolean isEmpty = true;
        for (int x = 0; x < widthAndHeight[0]; x++) {
            for (int y = 0; y < widthAndHeight[1]; y++) {
                if (gol.isCellAlive(x,y ))
                {
                    newGol.setCellAlive(x,y);
                    if (isEmpty){
                        isEmpty = false;
                    }
                }
            }
        }

        if (isEmpty){
            cView.boardOffsetX = 0;
            cView.boardOffsetY = 0;
        }
    }

    /**
     * Sets up all the listeners needed for the simulation of Game of Life
     */
    private void initializeListeners() {

        // sets up listeners for mouse actions on canvas
        canvas.setOnMouseReleased(this::mouseClick);
        canvas.setOnMouseDragged(this::mouseDrag);
        canvas.setOnMouseMoved(this::mouseTrace);
        canvas.setOnScroll(this::mouseScroll);
        canvas.setOnMouseExited(this::mouseCanvasExit);
        canvas.setOnMouseEntered(this::mouseCanvasEnter);

        //sets up keyboard listeners
        masterController.getScene().setOnKeyPressed(this::keyPressed);
        masterController.getScene().setOnKeyReleased(this::keyReleased);

        // binds the canvas to the stage size
        canvas.widthProperty().addListener(evt -> renderCanvasIfLowFPS());
        canvas.heightProperty().addListener(evt -> renderCanvasIfLowFPS());
    }

    /**
     * Called when the stage changes size
     */
    private void renderCanvasIfLowFPS() {
        if (!running || frameDelay > 0)
            renderCanvas();
    }

    /**
     * Called when key is pressed
     * @param keyEvent Contains information about which key is pressed
     */
    private void keyPressed(KeyEvent keyEvent) {

        // stores the key code
        String code = keyEvent.getCode().toString();

        // checks if key is already in list
        if (!buttonsPressed.contains(code)){

            // adds key in key list
            buttonsPressed.add(code);
        }

        if (code.equals("D")) {
            changeToDynamic();
        }
        if (code.equals("F")) {
            changeToStatic();
        }
        if (code.equals("Z")) {
            rotateImportLeft();
        }
        if (code.equals("X")) {
            rotateImportRight();
        }
        System.out.println(code);
        if (code.equals("MINUS")) {
            times*=2;
        }
        if (code.equals("SLASH")) {
            if(times > 1) times/=2;
        }

        // checks if "C" is pressed
        switch (code) {
            case "C":

                // checks if "CONTROL" or "COMMAND" is held
                if (buttonsPressed.contains("CONTROL") || buttonsPressed.contains("COMMAND")) {
                    copyMarkedArea();
                }
                break;
            // checks if "X" is pressed
            case "X":

                // checks if "CONTROL" or "COMMAND" is held
                if (buttonsPressed.contains("CONTROL") || buttonsPressed.contains("COMMAND")) {
                    cutMarkedArea();
                }
                break;
            // checks if "V" is pressed
            case "V":

                // checks if "CONTROL" or "COMMAND" is held
                if (buttonsPressed.contains("CONTROL") || buttonsPressed.contains("COMMAND")) {
                    pasteClipBoard();
                }

                break;
        }
    }

    /**
     * Rotates the clipboard pattern 90 degrees clockwise.
     */
    private void rotateImportRight() {

        if (clipBoardPattern == null){
            return;
        }
        boolean [][] temp = new boolean[clipBoardPattern[0].length][clipBoardPattern.length];
        for (int x = 0; x < clipBoardPattern.length; x++) {
            for (int y = 0; y < clipBoardPattern[0].length; y++) {
                temp[temp.length - (y +1)][x] = clipBoardPattern[x][y];
            }
        }
        clipBoardPattern = temp;
        renderCanvasIfLowFPS();
    }

    /**
     * Rotates the clipboard pattern 90 degrees counterclockwise.
     */
    private void rotateImportLeft() {

        if (clipBoardPattern == null){
            return;
        }
        boolean [][] temp = new boolean[clipBoardPattern[0].length][clipBoardPattern.length];
        for (int x = 0; x < clipBoardPattern.length; x++) {
            for (int y = 0; y < clipBoardPattern[0].length; y++) {
                temp[y][temp[0].length - (x + 1)] = clipBoardPattern[x][y];
            }
        }
        clipBoardPattern = temp;
        renderCanvasIfLowFPS();
    }

    /**
     * Called when key is released
     * @param keyEvent Contains information about which key is released
     */
    private void keyReleased(KeyEvent keyEvent) {

        // stores the key code
        String code = keyEvent.getCode().toString();

        // checks if list contains code
        if (buttonsPressed.contains(code)){
            // removes code from list
            buttonsPressed.remove(code);
        }
    }

    /**
     * Stores the position of the event
     * @param mouseEvent Event created by mouse action
     */
    private void mouseTrace(MouseEvent mouseEvent) {

        // checks if necessary to track position
        if (importing) {
            // stores current mouse coordinates
            currMousePosX = (int) mouseEvent.getX();
            currMousePosY = (int) mouseEvent.getY();

            // renders the canvas if animation is not running, or framerate is low
            if (!running || frameDelay > 0) {
                renderCanvas();
            }
        }
    }

    /**
     * To keep track if mouse is located on the canvas.
     * @param mouseEvent Event created by mouse action
     */
    private void mouseCanvasEnter(MouseEvent mouseEvent) {
        mouseOnCanvas = true;
    }

    /**
     * To keep track if mouse is located outside of the canvas.
     * Sets previous mouse coordinates to zero if exited.
     * @param mouseDragEvent Event created by mouse action
     */
    private void mouseCanvasExit(MouseEvent mouseDragEvent) {

        mouseOnCanvas = false;
        prevMousePosX = 0;
        prevMousePosY = 0;
    }

    /**
     * Handles mouse click on canvas. Calculates which cell is clicked on canvas.
     * Calculation based on cell size and grid offset.
     * Changes state on clicked cell.
     * If right click, simulation will pause.
     * @param mouseEvent Event created by mouse action
     */
    private void mouseClick(MouseEvent mouseEvent) {

 // if click is end of drag, reset variables used in drag
        if (mouseDrag) {
            prevMousePosX = 0;
            prevMousePosY = 0;
            mouseDrag = false;
            return;
        }

        // gets button from event
        MouseButton mouseButton = mouseEvent.getButton();

        // checks if left click
        if (mouseButton == MouseButton.PRIMARY) {

            //lets nextGeneration() finish
            waitForThread();

            // inserts clipboard pattern if in use
            if (importing) {
                insertImport();
                importing = false;
                return;
            }

            // gets position of cell on click position
            int gridClickX = getGridPosX(mouseEvent.getX());
            int gridClickY = getGridPosY(mouseEvent.getY());

            // makes sure the cell is on the grid
            fitTo(gridClickX, gridClickY);

            // changes state of cell
            gol.changeCellState((gridClickX < 0)? 0 : gridClickX, (gridClickY < 0)? 0 : gridClickY);

            // updates cellCounter
            giveCellCount();

            // updates canvas
            if (!running || frameDelay > 0)
                renderCanvas();

            // checks if right click
        } else if (mouseEvent.getButton() == MouseButton.SECONDARY) {

            // pause if running, play if paused
            if (running) {
                masterController.getToolController().changeButtonIconToPlay();
                stopAnimation();
                running = false;
            } else {
                masterController.getToolController().changeButtonIconToPause();
                startAnimation();
                running = true;
            }
        }
    }

    /**
     * If left mouse button clicked and mouse dragged, will draw path on canvas.
     * If right mouse button clicked and mouse dragged, will pan the grid.
     * @param mouseEvent Created by mouse action
     */
    private void mouseDrag(MouseEvent mouseEvent){

        // checks if mouse is on canvas
        if (!mouseOnCanvas)
            return;

        // gets the button clicked
        MouseButton b = mouseEvent.getButton();
        mouseDrag = true;

        // gets mouse coordinates on canvas.
        currMousePosX = (int) mouseEvent.getX();
        currMousePosY = (int) mouseEvent.getY();

        // checks if left click
        if (b == MouseButton.PRIMARY) {

            //lets nextGeneration finish
            waitForThread();
            // checks if not start of drag
            if (prevMousePosX != 0 || prevMousePosY != 0) {

                // draws a line from last mouse position to current position
                drawLine(getGridPosX(currMousePosX), getGridPosY(currMousePosY),
                        getGridPosX(prevMousePosX), getGridPosY(prevMousePosY));

                // updates the cell counter
                giveCellCount();

            } else {
                // gets cell on event position
                int x = getGridPosX(currMousePosX);
                int y = getGridPosY(currMousePosY);

                // makes sure the cell is on the grid
                fitTo(x, y);

                // sets cell alive
                gol.setCellAlive(x =(x < 0)?0:x, y = (y < 0)? 0 : y);

                // updates cell counter
                giveCellCount();

                // draws the cell at (x, y)
                drawCell(x, y);
            }
            // checks if right click
            } else if (mouseEvent.getButton() == MouseButton.SECONDARY) {

            // moves board to current position
            moveBoard(currMousePosX, currMousePosY);

            // updates the canvas if framerate is low
            if (!running || frameDelay > 0)
                renderCanvas();
        }

        // stores current mouse position for later use
        prevMousePosX = currMousePosX;
        prevMousePosY = currMousePosY;
    }

    /**
     * Moves the board in the same distance and direction as it is from the previous mouse position to the current
     * mouse position
     * @param currMousePosX Current mouse X coordinate
     * @param currMousePosY Current mouse Y coordinate
     */
    private void moveBoard(int currMousePosX, int currMousePosY) {

        // checks if last mouse position is present
        if (prevMousePosX != 0 || prevMousePosY != 0) {

            // moves the board using the offset
            cView.boardOffsetX += prevMousePosX - currMousePosX;
            cView.boardOffsetY += prevMousePosY - currMousePosY;
        }
    }

    /**
     * makes sure that x and y are inside the game board, if the board is dynamic
     * @param x The X coordinate of the cell to fit the board to
     * @param y The Y coordinate of the cell to fit the board to
     */
    private void fitTo(int x, int y) {

        // checks x is negative
        if(x < 0){
            // extends the board to fit to x
            ((DynamicGameOfLife) gol).increaseXLeft(Math.abs(x));
           // boardOffsetX -= (x - 1) * cell.getSize();
        }
        // checks if y is negative
        if(y < 0){
            // extends the board to fit to x
            ((DynamicGameOfLife)gol).increaseYTop(Math.abs(y));
            //boardOffsetY -= (y - 1) * cell.getSize();
        }
    }

    /**
     * Changes the cellSize to give the effect of zooming.
     * @param scrollEvent Event created by mouse scroll
     */
    private void mouseScroll(ScrollEvent scrollEvent) {

        // gets exact cell position at mouse coordinates
        double absMPosXOnGrid = (cView.getCommonOffsetX(gol, cell.getSize())
                + scrollEvent.getX()) / cell.getSize();

        double absMPosYOnGrid = (cView.getCommonOffsetY(gol, cell.getSize())
                + scrollEvent.getY()) /  cell.getSize();

        // changes cellsize
        cell.setSize(cell.getSize() * ( 1 + (scrollEvent.getDeltaY() / 150)));

        // updates slider
        masterController.getToolController().setZoom( cell.getSize());

        // moves the board so the mouse gets the exact same position on the board as before
        cView.boardOffsetX = (absMPosXOnGrid - gol.getOffsetX()) * cell.getSize() - scrollEvent.getX();
        cView.boardOffsetY = (absMPosYOnGrid - gol.getOffsetY()) * cell.getSize() - scrollEvent.getY();

        // checks if it is necessary to draw grid lines
        checkIfShouldStillDrawGrid();

        // changes the speed with horizontal scroll
        masterController.getToolController().addSpeedValue(scrollEvent.getDeltaX() / 5);

        // updates canvas if framerate is low
        renderCanvasIfLowFPS();
    }

    /**
     * Checks if grid lines should be displayed
     */
    private void checkIfShouldStillDrawGrid() {
        gridLines = cell.getSize() > 5;
    }

    // region canvas to grid converter

    /**
     * Converts horizontal mouse position on canvas to horizontal cell position on grid
     * @param x X coordinate for mouse
     * @return X coordinate on grid
     */
    private int getGridPosX(double x) {

        return (int)Math.floor((x + cView.getCommonOffsetX(gol, cell.getSize())) / cell.getSize());
    }

    /**
     * Converts vertical mouse position on canvas to vertical cell position on grid
     * @param y Y coordinate for mouse
     * @return Y coordinate on grid
     */
    private int getGridPosY(double y) {

        return (int) Math.floor((y + cView.getCommonOffsetY(gol, cell.getSize())) / cell.getSize());
    }
    // endregion

    //region grid to canvas converter

    /**
     * Converts grid coordinate x to canvas coordinate x.
     *
     * @param x a x coordinate in the grid.
     * @return a x coordinate on the canvas.
     */
    public double getCanvasPosX(int x) {

        return x * cell.getSize() -
                cView.getCommonOffsetX(gol, cell.getSize());
    }

    /**
     * Converts grid coordinate y to canvas coordinate y.
     *
     * @param y a y coordinate in the grid.
     * @return a y coordinate on the canvas.
     */
    public double getCanvasPosY(int y) {

        return y * cell.getSize() -
                cView.getCommonOffsetY(gol, cell.getSize());
    }
    //endregion

    /**
     * Renders everything on the canvas
     */
    void renderCanvas() {

        // checks wich cells are inside the canvas view
        cView.updateView(gol, cell.getSize(), (int)canvas.getWidth(), (int)canvas.getHeight());

        // renders the cells on the canvas
        renderLife();

        // checks if should render the clipboard pattern
        if (importing)
            renderImport();

        // checks if should draw grid lines
        if (gridLines)
            // checks if the user has set grid lines on or of
            if (userWantsGridLines)
                renderGridLines();

        // checks if something is marked
        if (markup != null){
            // renders the marked area
            renderMarkup();
        }
        //to see where the grid is
        if (gol instanceof StaticGameOfLife) {
            gc.setLineWidth(2);
            gc.strokeRect(-cView.getCommonOffsetX(gol, cell.getSize()), -cView.getCommonOffsetY(gol, cell.getSize()), gol.getGridWidth() * cell.getSize(), gol.getGridHeight() * cell.getSize());
        }
    }

    /**
     * Renders the current state of the game of life simulation to the canvas.
     * Sets background color and cell color.
     */
    private void renderLife() {

        // fills the background
        gc.setFill(cell.getDeadColor());
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        gc.setFill(cell.getColor());

        // runs through the cells inside the view
        for (int x = cView.currViewMinX; x <= cView.currViewMaxX; x++) {
            for (int y = cView.currViewMinY; y <= cView.currViewMaxY; y++) {

               try {
                   // draws the cells that are alive

                   if (gol.isCellAlive(x, y))
                       drawCell(x, y);
               }
                catch (NullPointerException ignored){
                }
            }
        }
    }

    /**
     * Draws the grid lines on the canvas
     */
    private void renderGridLines() {

        // sets the correct color
        gc.setStroke(cell.getGhostColor());
        gc.setLineWidth(1);

        // stores the x coordinate of the line that is used for drawing grid lines
        double xCoordinate;
        for (double i = 0; i < canvas.getWidth(); i += cell.getSize()){

            // draws vertical line number "i" inside the canvas view
            gc.strokeLine(xCoordinate = -cView.getCommonOffsetX(gol,
                    cell.getSize()) % cell.getSize() +
                    i - cell.getSpacingInPixels() / 2, 0,
                    xCoordinate, canvas.getHeight());
        }

        // stores the y coordinate of the line that is used for drawing grid lines
        double yCoordinate;
        for (double i = 0; i < canvas.getHeight(); i += cell.getSize()){

            // draws horizontal line number "i" inside the canvas view
            gc.strokeLine(0, yCoordinate = -cView.getCommonOffsetY(gol,
                    cell.getSize()) % cell.getSize() +
                    i - cell.getSpacingInPixels() / 2,
                    canvas.getWidth(), yCoordinate);
        }
    }

    /**
     * Renders the imported pattern on the canvas around the mouse position
     */
    private void renderImport() {

        // sets the correct color
        gc.setFill(cell.getGhostColor());

        // draws cells with mouse position as center of pattern
        for (int x = 0; x < clipBoardPattern.length; x++) {
            for (int y = 0; y < clipBoardPattern[x].length; y++) {
                if (clipBoardPattern[x][y]) {
                    drawCell(getGridPosX(currMousePosX) - clipBoardPattern.length / 2 + x, getGridPosY(currMousePosY) - clipBoardPattern[x].length / 2 + y);
                }
            }
        }
    }

    /**
     * Inserts the imported pattern into the cell grid
     */
    private void insertImport() {

        for (int x = 0; x < clipBoardPattern.length; x++) {
            for (int y = 0; y < clipBoardPattern[x].length; y++) {

                // checks if current cell is alive
                if (clipBoardPattern[x][y]) {

                    // gets the current cell's position on the main board
                    int posX = getGridPosX(currMousePosX) - clipBoardPattern.length / 2 + x;
                    int posY = getGridPosY(currMousePosY) - clipBoardPattern[x].length / 2 + y;

                    // checks if it is inside the board
                    if (posX < 0 || posY < 0) {
                        // fits to the current cell
                        fitTo(posX, posY);
                        // same as above, but the board has now moved to fit the cell
                        posX = getGridPosX(currMousePosX) - clipBoardPattern.length / 2 + x;
                        posY = getGridPosY(currMousePosY) - clipBoardPattern[x].length / 2 + y;
                    }

                    // sets the cell alive
                    gol.setCellAlive(posX, posY);
                }
            }
        }
        // import should no longer be rendered
        importing = false;

        // updates cell counter
        giveCellCount();

        // updates canvas if framerate is low
        renderCanvasIfLowFPS();
    }

    /**
     * Kills all cells on grid
     */
    void clearGrid() {

        //lets nextGeneration() finish
        waitForThread();

        // empty board
        gol.clearGrid();

        // update cell counter
        giveCellCount();

        // updates canvas
        renderCanvas();
    }

    /**
     * Draws the cell at the x, y coordinate in the grid
     *
     * @param x The x coordinate in the game of life grid.
     * @param y The y coordinate in the game of life grid.
     */
    private void drawCell(int x, int y) {
        // draws the cell at the x, y coordinate in the grid
        gc.fillRect(getCanvasPosX(x), getCanvasPosY(y), cell.getSize() - cell.getSpacingInPixels(), cell.getSize() - cell.getSpacingInPixels());
    }

    /**
     * creates a line of alive cells on the grid from cell (x,y) to cell (x2, y2)
     *
     * @param x  THe first coordinate of the first cell
     * @param y  The second coordinate of the first cell
     * @param x2 The first coordinate of the end cell
     * @param y2 The second coordinate of the end cell
     */
    private void drawLine(int x, int y, int x2, int y2) {

        // checks offset before fitting board
        int offsetX = gol.getOffsetX();
        int offsetY = gol.getOffsetY();

        // fits board to (x, y)
        fitTo(x, y);

        // gets the amunt the board has moved
        offsetX -= gol.getOffsetX();
        offsetY -= gol.getOffsetY();

        // moves coordinates the same amount
        x -= offsetX;
        x2 -= offsetX;
        y -= offsetY;
        y2 -= offsetY;

        // horizontal and vertical dimensions of line
        int width = x2 - x;
        int height = y2 - y;

        // stores the longest dimension, number of cells to draw
        int lineLength = Math.abs((Math.abs(width) < Math.abs(height)) ? height : width);

        // stores the current point to draw and set alive
        int x1, y1;
        for (int i = 0; i < lineLength; i++) {

            // calculates each cell position
            gol.setCellAlive(x1 = x + i * width / lineLength, y1 = y + i * height / lineLength);
            drawCell(x1, y1);
        }
    }

    private void waitForThread(){
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //region Animation control

    /**
     * Start animation
     */
    void startAnimation() {

        animationTimer.start();
        running = true;
    }

    /**
     * Stop animation
     */
    void stopAnimation() {

        animationTimer.stop();

        // lets next generation finish, then draws it on the canvas
        waitForThread();
        renderCanvas();
        running = false;
    }
    //endregion

    //region Setters

    void setLiveColor(Color liveColor) { cell.setColor(liveColor); }

    /**
     * Sets the background color used to render the canvas
     * @param deadColor the color to be set
     */
    void setDeadColor(Color deadColor) { cell.setDeadColor(deadColor); }

    /**
     * Setts the cell size to be the newCellSize
     *
     * @param newCellSize the new cell size
     */
    void setCellSize(double newCellSize) {

        // stores the exact center of the canvas view
        double canvasCenterX = (cView.getCommonOffsetX(gol, cell.getSize()) +
                canvas.getWidth() / 2) / cell.getSize();

        double canvasCenterY = (cView.getCommonOffsetY(gol, cell.getSize()) +
                canvas.getHeight() / 2) / cell.getSize();

        // sets the new cellSize
        cell.setSize(newCellSize);

        // moves the board to get the same center of view as before
        cView.boardOffsetX = (int) (cell.getSize() * canvasCenterX -
                canvas.getWidth() / 2 - gol.getOffsetX() * cell.getSize());

        cView.boardOffsetY = (int) (cell.getSize() * canvasCenterY -
                canvas.getHeight() / 2 - gol.getOffsetY() * cell.getSize());

        // checks if still necessary to draw grid lines
        checkIfShouldStillDrawGrid();

        // updates canvas if framerate is low
        renderCanvasIfLowFPS();
    }

    /**
     * Sets the pattern that is imported from a file
     *
     * @param clipBoardPattern the pattern that is imported from a file
     */
    void setClipBoardPattern(boolean[][] clipBoardPattern) {

        this.clipBoardPattern = clipBoardPattern;

        // checks if pattern is null
        if (clipBoardPattern != null) {
            importing = true;

            String importedRule = PatternParser.getLastImportedRule();

            if (importedRule != null) {
                try {

                    if (!RuleParser.formatRuleText(importedRule).equals(gol.getRule().toString())) {
                        showDifferentRule(importedRule);
                    }

                } catch (RuleFormatException ignored) {
                    MessageBox.alert("Unknown rule: " + importedRule);
                }
            }
        }
    }

    /**
     * Asks the user if they want to change the rules of the game
     * @param importedRule The rule the user can change to.
     */
    private void showDifferentRule(String importedRule) {

        interaction = true;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Wrong rule");
        alert.setHeaderText("The pattern you imported use a different rule than what you have now");
        alert.setContentText("Do you want to change rule from " + gol.getRule().toString() + " to " + importedRule + "?");

        ButtonType yesBtn = new ButtonType("Yes");
        ButtonType noBtn = new ButtonType("No");
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(yesBtn, noBtn, cancelBtn);

        Optional<ButtonType> result = alert.showAndWait();
        //noinspection OptionalGetWithoutIsPresent
        if (result.get() == yesBtn){
            setRule(importedRule);
        }
        else if(result.get() == noBtn){
            // do nothing
        }
        else {
            // ... user chose CANCEL or closed the dialog
            importing = false;
        }
        interaction = false;
    }

    /**
     * Sets the frame delay
     * @param frameDelay The delay to be set
     */
    void setFrameDelay(int frameDelay) {

        // if frame delay is less than 17, it can be 0
        if (frameDelay < 17) // 60 fps
            this.frameDelay = 0;
        else
            this.frameDelay = frameDelay;
    }

    /**
     * Sets the rule
     * @param ruleText The rule to be set, in Born/Survive format
     */
    public void setRule(String ruleText) {
        // sets the rule
        gol.setRule(ruleText);
        masterController.getToolController().setRuleLabel(gol.getRule());
    }

    /**
     * Sends the cellCount to the cell counter in toolController
     */
    private void giveCellCount() {

        masterController.getToolController().giveCellCount(gol.getCellCount());
    }

    public void setInteraction(boolean interaction) {
        this.interaction = interaction;
    }

    //endregion

    //region getters
    public Canvas getCanvas() {
        return canvas;
    }

    public CameraView getCameraView(){
        return cView;
    }

    public Cell getCell() {
        return cell;
    }

    public MasterController getMasterController() {
        return masterController;
    }

    //endregion

    // region s305080 extra task

    /**
     * Opens the gif saver
     */
    void saveToGif(){
        try {
            if (markup == null){
                new GifSaver().saveCanvasAsGif(this);
            }
            else {
                new GifSaver().saveGifFromSelectedArea(this, getMinMaxValues());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Saves pattern to file
     */
    void saveToFile() {
        interaction = true;
        new ToFile().writeToFile(gol, masterController.getStage());
        interaction = false;
    }

    /**
     * Enables selection of area in grid through canvas position
     */
    void activateMarkup() {
        canvas.setOnMouseDragged(this::mouseDragMarkup);
    }

    /**
     * Disables selection of area in grid through canvas position
     */
    void deactivateMarkup() {
        canvas.setOnMouseDragged(this::mouseDrag);
        removeMarkedArea();
    }

    /**
     * Renders the line around the marked area
     */
    private void renderMarkup() {

        // sets teh correct color
        gc.setStroke(cell.getGhostColor());

        // sets the correct line width
        gc.setLineWidth(4);

        // strokes the rectangle around the marked area
        gc.strokeRect(getCanvasPosXMarkup(Math.floor((markup[0] < markup[2])?markup[0]:markup[2])) - cell.getSpacingInPixels() / 2,
                getCanvasPosYMarkup(Math.floor((markup[1] < markup[3])?markup[1]:markup[3])) - cell.getSpacingInPixels() / 2,
                (Math.abs(Math.floor(markup[0]) - Math.floor(markup[2])) + 1) * cell.getSize(),
                (Math.abs(Math.floor(markup[1]) - Math.floor(markup[3])) + 1) * cell.getSize());

        //getCanvasPosX(markup[1])
        //getCanvasPosY(markup[3])
    }

    /**
     * Similar to the getCanvasPosX, but more accurate using double
     * @param x A exact grid position
     * @return The coordinate on the canvas
     */
    private double getCanvasPosXMarkup(double x) {
        return x * cell.getSize() - cView.boardOffsetX;
    }

    /**
     * Similar to the getCanvasPosY, but more accurate using double
     * @param y A exact position on the grid
     * @return The coordinate on the canvas
     */
    private double getCanvasPosYMarkup(double y) {
        return y * cell.getSize() - cView.boardOffsetY;
    }

    /**
     * Same as mouseDrag(), but left click selects an area
     * @param mouseEvent Created by mouse action
     */
    private void mouseDragMarkup(MouseEvent mouseEvent){

        // checks if mouse is on canvas
        if (!mouseOnCanvas)
            return;

        // stores the button
        MouseButton b = mouseEvent.getButton();

        // gets mouse coordinates on canvas.
        currMousePosX = (int) mouseEvent.getX();
        currMousePosY = (int) mouseEvent.getY();

        // checks if left click
        if (b == MouseButton.PRIMARY) {

            // checks if marked area is null
            if (markup == null){

                // creates new marked area
                markup = new double[4];
            }

            // checks if start of drag
            if(!mouseDrag){

                // sets market area as current position
                markup[0] = (mouseEvent.getX() + cView.boardOffsetX) / cell.getSize();
                markup[1] = (mouseEvent.getY() + cView.boardOffsetY) / cell.getSize();
                markup[2] = (mouseEvent.getX() + cView.boardOffsetX) / cell.getSize();
                markup[3] = (mouseEvent.getY() + cView.boardOffsetY) / cell.getSize();
            }
            else{
                // sets corner of marked area as current position
                markup[2] = (mouseEvent.getX() + cView.boardOffsetX) / cell.getSize();
                markup[3] = (mouseEvent.getY() + cView.boardOffsetY) / cell.getSize();
            }
            // checks if right click
        } else if (mouseEvent.getButton() == MouseButton.SECONDARY) {

            // moves board from last mouse position to current mouse position
            moveBoard(currMousePosX, currMousePosY);
        }

        // updates canvas if framerate is low
        renderCanvasIfLowFPS();

        // stores current mouse position for later use
        prevMousePosX = currMousePosX;
        prevMousePosY = currMousePosY;

        mouseDrag = true;
    }

    /**
     * Decides if there is a clipboard pattern to show
     */
    void pasteClipBoard() {
        if (clipBoardPattern != null){
            importing = true;
            renderCanvasIfLowFPS();
        }
    }

    /**
     * Copies the pattern inside the selected area
     */
    void copyMarkedArea() {
        if (markup == null){
            return;
        }

        // gets the min and max x and y values inside the marked area
        int [] minMaxValues = getMinMaxValues();

        // creates a new grid with the same dimensions as the selected area
        boolean[][] clipboard = new boolean[1 + minMaxValues[2] - minMaxValues[0]][1 + minMaxValues[3] - minMaxValues[1]];

        // current X and Current Y in clipboard
        int cX = 0, cY = 0;

        // runs through every cell inside the selected area
        for (int x = minMaxValues[0]; x <=  minMaxValues[2]; x++) {
            for (int y = minMaxValues[1]; y <= minMaxValues[3]; y++) {
                    if (gol.isCellAlive(x, y)){
                        clipboard[cX][cY] = true;
                    }

                cY++;
            }
            cX++;
            cY = 0;
        }

        clipBoardPattern = clipboard;

        // updates canvas if framerate is low
        renderCanvasIfLowFPS();
    }

    /**
     * Same as copyMarkedAre() but it kills the cells in the selected area as well
     */
    void cutMarkedArea() {

        if (markup == null){
            return;
        }
        //lets nextGeneration finish
        waitForThread();
        // gets the min and max x and y values inside the marked area
        int [] minMaxValues = getMinMaxValues();

        // creates a new grid with the same dimensions as the selected area
        boolean[][] clipboard = new boolean[1 + minMaxValues[2] - minMaxValues[0]][1 + minMaxValues[3] - minMaxValues[1]];

        // current X and Current Y in clipboard
        int cX = 0, cY = 0;

        // runs through every cell inside the selected area
        for (int x = minMaxValues[0]; x <=  minMaxValues[2]; x++) {
            for (int y = minMaxValues[1]; y <= minMaxValues[3]; y++) {
                    if (gol.isCellAlive(x, y)){
                        clipboard[cX][cY] = true;
                    }
                    try {
                        gol.setCellDead(x, y);
                    }
                    catch (IndexOutOfBoundsException ignored){
                        // the cell is dead
                    }

                cY++;
            }
            cX++;
            cY = 0;
        }

        // updates canvas if framerate is low
        clipBoardPattern = clipboard;
        renderCanvasIfLowFPS();

        removeMarkedArea();

        renderCanvasIfLowFPS();
    }

    /**
     * Calculates the minimum and maximum x and y values inside the selected area
     * @return An array where the first 2 values is min and max x, and the last 2 is min and max y
     */
    private int[] getMinMaxValues() {
        int [] cornerCells = new int [4];
        cornerCells[0] = getGridPosX(getCanvasPosXMarkup((markup[0] < markup[2]) ? markup[0] : markup[2]));
        cornerCells[1] = getGridPosY(getCanvasPosYMarkup((markup[1] < markup[3]) ? markup[1] : markup[3]));
        cornerCells[2] = getGridPosX(getCanvasPosXMarkup((markup[0] > markup[2]) ? markup[0] : markup[2]));
        cornerCells[3] = getGridPosY(getCanvasPosYMarkup((markup[1] > markup[3]) ? markup[1] : markup[3]));
        return cornerCells;
    }

    /**
     * Deletes the markup array
     */
    private void removeMarkedArea() {
        markup = null;
    }

    public GameOfLife getGol() {
        return gol;
    }

    //endregion
}