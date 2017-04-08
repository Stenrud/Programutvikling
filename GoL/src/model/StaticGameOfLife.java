package model;

import com.sun.org.apache.xpath.internal.operations.Bool;
import model.rules.RuleParser;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Pair programming
 *
 * Game class with a game board with static size.
 */
public class StaticGameOfLife extends GameOfLife{

    // game board
    private boolean[][] grid;
    private byte[][] neighbours;

    //region start-up

    /**
     * StaticGameOfLife Constructor. Sets the classic Conway rule (B3/S23) as default rule.
     *
     * @param width Width of the game board
     * @param height Height of the game board
     */
    public StaticGameOfLife(int width, int height) {

        this(width, height, RuleParser.CLASSIC_RULESTRING);
    }

    /**
     * StaticGameOfLife Constructor.
     * Sets the rule based on the parameter rulestring.
     */
    public StaticGameOfLife(int width, int height, String rulestring) {

        createGameBoard(width, height);
        setRule(rulestring);
    }

    /**
     * Creates the boolean 2D Array to keep track of dead and live cells, and the 2D byte-
     * array to keep track of the neighbour count to the corresponding cells in the other array
     */
    private void createGameBoard(int width, int height) {

        grid = new boolean[width][height];
        neighbours = new byte[width][height];

        for (int x = 0; x < width; x++){

            for (int y = 0; y < height; y++){
                grid[x][y] = false;
                neighbours[x][y] = 0;
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
    public AtomicInteger[][] getNeighbours() {
        return new AtomicInteger[2][2];
    }

    /**
     * Getter for the cell-2D-array
     *
     * @return The cell-2D-array
     */
    public AtomicBoolean[][] getGrid() {
        return new AtomicBoolean[2][2];
    }

    @Override
    public int getGridWidth() {
        return grid.length;
    }

    @Override
    public int getGridHeight() {
        return grid[0].length;
    }

    @Override
    public int getNeighboursAt(int x, int y) {
        return neighbours[x][y];
    }

    @Override
    public boolean isCellAlive(int x, int y) {
        try {
            return grid[x][y];
        } catch (ArrayIndexOutOfBoundsException e){
            return false;
        }
    }

    /**
     * Clones the StaticGameOfLife object
     *
     * @return the cloned StaticGameOfLife object
     */
    @Override
    public StaticGameOfLife clone() {

        StaticGameOfLife staticGameOfLife = new StaticGameOfLife(
                getGridWidth(), getGridHeight(), getRule().toString());
        staticGameOfLife.deepCopyOnSet(grid);
        staticGameOfLife.setCellCount(cellCount.get());

        return staticGameOfLife;
    }

    /**
     * Deep copies the grid and sets it.
     *
     * @param grid the grid to be deep copied and set.
     */
    public void deepCopyOnSet(boolean[][] grid) {

        boolean[][] copiedBoard = new boolean[grid.length][grid[0].length];
        neighbours = new byte[grid.length][grid[0].length];
        cellCount.set(0);

        for (int x = 0; x < grid.length; x++) {
            for (int y = 0; y < grid[0].length; y++) {

                copiedBoard[x][y] = grid[x][y];
                neighbours[x][y] = 0;

                if(grid[x][y]) {
                    cellCount.incrementAndGet();
                }
            }
        }
        this.grid = copiedBoard;
    }

    //endregion

    //region setters

    /**
     * Sets the cell grid to be used
     *
     * @param grid Cell grid
     */
    public void setGrid(boolean[][] grid) {
        this.grid = grid;
    }

    @Override
    public void setCellAlive(int x, int y) {

        if(!isCellAlive(x,y)){

            try {
                grid[x][y]=true;
                cellCount.incrementAndGet();
            } catch (IndexOutOfBoundsException ignored){
            }
        }
    }

    @Override
    public void setCellDead(int x, int y) {

        if(isCellAlive(x,y)) {

            grid[x][y]=false;
            cellCount.decrementAndGet();
        }
    }
    @Override
    public void aggregateNeighbours(int startColumn, int stopColumn) {

        if(startColumn != stopColumn) {
            synchronized (grid[startColumn - 1]){
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
        for (int x = startColumn + 1 ; x < stopColumn; x++) {
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

    @Override
    public void clearGrid() {

        for (int x = 0; x < getGridWidth(); x++) {
            for (int y = 0; y < getGridHeight(); y++) {
                grid[x][y]=false;
                neighbours[x][y]=0;
            }
        }

        cellCount.set(0);
    }

    @Override
    protected void incrementNeighboursAt(int x, int y){ neighbours[x][y]++;}

    @Override
    public void resetNeighboursAt(int x, int y) {
        neighbours[x][y]=0;
    }

    //endregion
}