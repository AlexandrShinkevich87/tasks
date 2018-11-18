package game;

import org.junit.Assert;
import org.junit.Test;

import static game.Game.died;
import static game.Game.live;

public class GameTest {
    @Test
    public void shouldDiedWhenUnderPopulation() {
        // give
        byte [][] generation = new byte[][] {
                {0,0,0},
                {0,1,0},
                {0,0,0}
        };
        // then
        Game game = new Game();
        byte[][] newGeneration = game.nextGeneration(generation);

        Game.printBoard(newGeneration);

        // expect
        byte cell = newGeneration[1][1];

        Assert.assertEquals(cell, died);

    }

    @Test
    public void shouldLiveWhenSurvival() {
        // give
        byte [][] generation = new byte[][] {
                {0,0,0},
                {1,1,0},
                {0,1,1}
        };
        // then
        Game game = new Game();
        byte[][] newGeneration = game.nextGeneration(generation);

        Game.printBoard(newGeneration);

        // expect
        byte cell = newGeneration[1][1];

        Assert.assertEquals(cell, live);
    }
    @Test
    public void shouldDiedWhenOvercrowding() {
        // give
        byte [][] generation = new byte[][] {
                {1,1,1},
                {1,1,1},
                {1,1,1}
        };
        // then
        Game game = new Game();
        byte[][] newGeneration = game.nextGeneration(generation);

        Game.printBoard(newGeneration);

        // expect
        byte cell = newGeneration[1][1];

        Assert.assertEquals(cell, died);
    }

    @Test
    public void shouldLiveWhenReproduction() {
        // give
        byte [][] generation = new byte[][] {
                {0,0,0},
                {1,0,0},
                {0,1,1}
        };
        // then
        Game game = new Game();
        byte[][] newGeneration = game.nextGeneration(generation);

        Game.printBoard(newGeneration);

        // expect
        byte cell = newGeneration[1][1];

        Assert.assertEquals(cell, live);
    }
}