package game;

public class Game {
    private static byte rows = 3;
    private static byte cols = 3;

    static byte died = 0;
    static byte live = 1;

    private static final char DEAD_CELL_SYMBOL = '□';
    private static final char ALIVE_CELL_SYMBOL = '■';

    public static void printBoard(byte[][] board) {
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                switch (board[i][j]) {
                    case 0:
                        System.out.print(DEAD_CELL_SYMBOL);
                        break;
                    case 1:
                        System.out.print(ALIVE_CELL_SYMBOL);
                        break;
                }
                System.out.print(' ');
            }
            System.out.println();
        }
        System.out.println();
    }

    public byte[][] nextGeneration(byte[][] grid) {

        byte[][] newGeneration = new byte[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (countNeighbors(i, j, grid) < 2) {
                    newGeneration[i][j] = 0;
                } else if (countNeighbors(i, j, grid) == 2) {
                    newGeneration[i][j] = 1;
                } else if (countNeighbors(i, j, grid) == 3) {
                    newGeneration[i][j] = 1;
                } else if (countNeighbors(i, j, grid) > 3) {
                    newGeneration[i][j] = 0;
                }
            }
        }
        return newGeneration;
    }

    public int countNeighbors(int row, int col, byte[][] grid) {
        int rightBound = Math.min(col + 1, cols - 1);
        int bottomBound = Math.min(row + 1, rows - 1);

        int cntNeighbors = 0;

        for (int i = Math.max(row - 1, 0); i <= bottomBound; i++) {
            for (int j = Math.max(col - 1, 0); j <= rightBound; j++) {
                boolean isAlive = grid[i][j] == 1;
                if (isAlive && (!(i == row && j == col))) {
                    cntNeighbors++;
                }
            }
        }
        return cntNeighbors;
    }
}
