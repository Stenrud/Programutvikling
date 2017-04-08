package model;

import model.rules.RuleParser;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Pair programming
 *
 * Game class with a game board with dynamic size.
 * The game board will expand or shrink to fit around the pattern
 */
public class DynamicGameOfLife extends GameOfLife{

    // game board
    private ArrayList<ArrayList<Boolean>> grid;
    private ArrayList<ArrayList<Integer>> neighbours;

    //region start up

    /**
     * DynamicGameOfLife Constructor.
     * Sets the classic Conway rule (B3/S23) as default rule.
     */
    public DynamicGameOfLife() {

        createGameBoard();
        setRule(RuleParser.CLASSIC_RULESTRING);
    }

    /**
     * DynamicGameOfLife Constructor.
     * Sets the rule based on the parameter rulestring.
     */
    public DynamicGameOfLife(String rulestring) {

        createGameBoard();
        setRule(rulestring);
    }

    /**
     * Creates the boolean 2D Array to keep track of dead and live cells, and the 2D byte-
     * array to keep track of the neighbour count to the corresponding cells in the other array
     */
    private void createGameBoard() {

        grid = new ArrayList<>();
        neighbours = new ArrayList<>();

        grid.add(new ArrayList<>());
        neighbours.add(new ArrayList<>());
        grid.get(0).add(new Boolean(false));
        neighbours.get(0).add(new Integer(0));
    }
    //endregion

    //region next generation

    @Override
    public void nextGeneration() {

        fitBoardToPattern();
        super.nextGeneration();
    }

    @Override
    public void aggregateNeighbours(int startColumn, int stopColumn) {

        if(startColumn != stopColumn) {
            synchronized (grid.get(startColumn - 1)){
                for (int y = 1; y < getGridHeight() - 1; y++) {

                    if (isCellAlive(startColumn, y)) {

                        for (int a = startColumn - 1; a <= startColumn + 1; a++) {
                            for (int b = y - 1; b <= y + 1; b++) {

                                if (a != startColumn || b != y) {
                                    incrementNeighboursAt(a, b);
                                }
                            }
                        }
                    }
                }
            }
        }
        if(startColumn + 1 < stopColumn) {
            synchronized (grid.get(startColumn)){
                for (int y = 1; y < getGridHeight() - 1; y++) {

                    if (isCellAlive(startColumn+1, y)) {

                        for (int a = startColumn+1 - 1; a <= startColumn+1 + 1; a++) {
                            for (int b = y - 1; b <= y + 1; b++) {

                                if (a != startColumn+1 || b != y) {
                                    incrementNeighboursAt(a, b);
                                }
                            }
                        }
                    }
                }
            }
        }
        for (int x = startColumn + 2 ; x < stopColumn; x++) {
            for (int y = 1; y < getGridHeight() - 1; y++) {

                if (isCellAlive(x,y)) {

                    for (int a = x - 1; a <= x + 1; a++) {
                        for (int b = y - 1; b <= y + 1; b++) {

                            if (a != x || b != y) {
                                incrementNeighboursAt(a,b);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Will expand or shrink the game board to fit around the pattern
     */
    public void fitBoardToPattern() {

        int [] bBox = getBoundingBox();
        if (bBox[0] > 1){
            decreaseXLeft(bBox[0] - 1);
            bBox[1] -= bBox[0] - 1;
        }
        else if(bBox[0] == 0){
            increaseXLeft(1);
            bBox[1] += 1;
        }
        if (bBox[1] < grid.size() - 2){
            decreaseXRight(grid.size() - bBox[1] - 2);
        }
        else if(bBox[1] == grid.size() - 1){
            increaseXRight(1);
        }

        if (bBox[2] > 1){
            decreaseYTop(bBox[2] - 1);
            bBox[3] -= bBox[2] - 1;
        }
        else if (bBox[2] == 0){
            increaseYTop(1);
            bBox[3] += 1;
        }
        if (bBox[3] < grid.get(0).size() - 2){
            decreaseYBottom(grid.get(0).size() - bBox[3] - 2);
        }
        else if(bBox[3] == grid.get(0).size() - 1){
            increaseYBottom(1);
        }
    }

    //endregion

    //region dynamic board handling

    /**
     * Will add a number of columns to the right side of the game board
     *
     * @param diffX Number of columns
     */
    private void increaseXRight(int diffX) {

        for (int i = 0; i < diffX; i++){

            grid.add(new ArrayList<>());
            neighbours.add(new ArrayList<>());

            for (int j = 0; j < grid.get(0).size(); j++) {

                grid.get(grid.size() - 1).add(new Boolean(false));
                neighbours.get(grid.size() - 1).add(new Integer(0));
            }
        }
    }

    /**
     * Will add a number of rows to the bottom side of the game board
     *
     * @param diffY Number of rows
     */
    private void increaseYBottom(int diffY){

        for (int i = 0; i < grid.size(); i++) {
            for (int j = 0; j < diffY; j++){

                grid.get(i).add(new Boolean(false));
                neighbours.get(i).add(new Integer(0));
            }
        }
    }

    /**
     * Will add a number of columns to the left side of the game board
     *
     * @param diffX Number of columns
     */
    public void increaseXLeft(int diffX) {

        cellOffsetX += diffX;

        for (int i = 0; i < diffX; i++){

            grid.add(0, new ArrayList<>());
            neighbours.add(0, new ArrayList<>());

            for (int j = 0; j < grid.get(1).size(); j++) {

                grid.get(0).add(new Boolean(false));
                neighbours.get(0).add(new Integer(0));
            }
        }
    }

    /**
     * Will add a number of rows to the top side of the game board
     *
     * @param diffY Number of rows
     */
    public void increaseYTop(int diffY){

        cellOffsetY += diffY;

        for (int i = 0; i < grid.size(); i++) {
            for (int j = 0; j < diffY; j++){

                grid.get(i).add(0, new Boolean(false));
                neighbours.get(i).add(0, new Integer(0));
            }
        }
    }

    /**
     * Will remove a number of columns from the right side of the game board
     *
     * @param diffX Number of columns
     */
    private void decreaseXRight(int diffX) {

        for (int i = 0; i < diffX; i++){

            grid.remove(grid.size() - 1);
            neighbours.remove(neighbours.size() - 1);
        }
    }

    /**
     * Will remove a number of row from the bottom side of the game board
     *
     * @param diffY Number of rows
     */
    private void decreaseYBottom(int diffY){

        for (int i = 0; i < grid.size(); i++) {
            for (int j = 0; j < diffY; j++){

                grid.get(i).remove(grid.get(i).size() - 1);
                neighbours.get(i).remove(neighbours.get(i).size() - 1);
            }
        }
    }

    /**
     * Will remove a number of columns from the left side of the game board
     *
     * @param diffX Number of columns
     */
    private void decreaseXLeft(int diffX) {

        cellOffsetX -= diffX;

        for (int i = 0; i < diffX; i++){

            grid.remove(0);
            neighbours.remove(0);
        }
    }

    /**
     * Will remove a number of row from the top side of the game board
     * @param diffY Number of rows
     */
    private void decreaseYTop(int diffY){

        cellOffsetY -= diffY;

        for (int i = 0; i < grid.size(); i++) {
            for (int j = 0; j < diffY; j++){

                grid.get(i).remove(0);
                neighbours.get(i).remove(0);
            }
        }
    }

    //endregion

    //region getters

    /**
     * Getter for neighbour-2D-array
     *
     * @return The neighbour-2D-array
     */
    public ArrayList<ArrayList<Integer>> getNeighbours() {
        return neighbours;
    }

    /**
     * Getter for the cell-2D-array
     *
     * @return The cell-2D-array
     */
    public ArrayList<ArrayList<Boolean>> getGrid() {
        return grid;
    }

    @Override
    public int getGridWidth(){ return grid.size(); }

    @Override
    public int getGridHeight(){ return grid.get(0).size(); }

    @Override
    public int getNeighboursAt(int x, int y){
        return neighbours.get(x).get(y);
    }

    @Override
    public boolean isCellAlive(int x, int y){

        if(grid == null)
            return false;

        try{
            return  grid.get(x).get(y);
        }
        catch(IndexOutOfBoundsException e){
            return false;
        }
    }

    /**
     * Clones the DynamicGameOfLife object
     * @return the cloned DynamicGameOfLife object
     */
    @Override
    public DynamicGameOfLife clone() {

        DynamicGameOfLife gameOfLife = new DynamicGameOfLife(getRule().toString());
        gameOfLife.deepCopyOnSet(grid);
        gameOfLife.setCellCount(cellCount.get());

        return gameOfLife;
    }

    /**
     * Deep copies the grid and sets it.
     * @param grid the grid to be deep copied and set.
     */
    private void deepCopyOnSet(ArrayList<ArrayList<Boolean>> grid) {

        neighbours.clear();
        cellOffsetX = 0;
        cellOffsetY = 0;
        this.grid.clear();

        for (int x = 0; x < grid.size(); x++) {

            this.grid.add(new ArrayList<>());
            neighbours.add(new ArrayList<>());

            for (int y = 0; y < grid.get(x).size(); y++) {

                this.grid.get(x).add(new Boolean(grid.get(x).get(y)));
                neighbours.get(x).add(new Integer(0));
            }
        }
    }
    //endregion

    //region setters

    /**
     * Sets the cell grid to be used
     *
     * @param grid Cell grid
     */
    public void setGrid(ArrayList<ArrayList<Boolean>> grid) {
        this.grid = grid;
    }

    @Override
    public void setCellAlive(int x, int y) {

        if(!isCellAlive(x,y)) {

            try {
                grid.get(x).set(y, true);
            }
            catch (IndexOutOfBoundsException e) {

                int diffX = x - getGridWidth() + 1;
                if(diffX > 0)
                    increaseXRight(diffX);

                int diffY = y - getGridHeight() + 1;
                if(diffY > 0)
                    increaseYBottom(diffY);

                grid.get(x).set(y, true);
            }

            cellCount.incrementAndGet();
        }
    }

    @Override
    public void setCellDead(int x, int y) {

        if(isCellAlive(x,y)) {

            grid.get(x).set(y, false);
            cellCount.decrementAndGet();
        }
    }

    @Override
    public void clearGrid() {

        grid.clear();
        neighbours.clear();

        grid.add(new ArrayList<>());
        neighbours.add(new ArrayList<>());

        grid.get(0).add(new Boolean(false));
        neighbours.get(0).add(new Integer(0));

        cellCount.set(0);
    }

    @Override
    protected void incrementNeighboursAt(int x, int y){ neighbours.get(x).set(y, neighbours.get(x).get(y) + 1); }

    @Override
    public void resetNeighboursAt(int x, int y){
        neighbours.get(x).set(y, 0);
    }
    //endregion
}
