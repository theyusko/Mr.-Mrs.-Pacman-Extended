package GameLogic;

import DataLayer.GameDatabase.GameData;
import DataLayer.GameDatabase.GameDataManager;
import GUI.GamePanel;
import GUI.UIManager;
import GameLogic.Enums.GhostType;
import GameLogic.Enums.PacmanType;
import GameLogic.InputManager.PacManMovementController;
import GameLogic.InputManager.PauseGameController;
import GameLogic.ScreenItems.Ghost;
import GameLogic.ScreenItems.Pacman;
import GameLogic.ScreenItems.Shield;
import GameLogic.UpdateManager.TimeController;
import javax.swing.*;

import static javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW;

/** GameEngine class : core game logic class, which holds and manipulates game entities and
 *  controls interaction with UI package
 * @author Ecem Ilgun, Talha Seker
 * @version 1.8
 * @since 1.0
 */
public class GameEngine {
    //Variables
    static final int MAX_LIFE = 3;
    private int numPlayer, numGhost, level, score, livesLeft;
    public int[][] gameMap, gameMapRestore;
    private Pacman[] pacmans, pacmansRestore;
    private Ghost[] ghosts, ghostsRestore;
    private GameMap map;
    private double counter;
    public boolean isPaused;
    private GamePanel gamePanel;
    private PacManMovementController pacmanMovementKeyBindings;
    private PauseGameController pauseGameController;
    private TimeController timeController;
    private UIManager uiManager;
    private GameDataManager gameDataManager;
    private GameData gameData;
    private int highScores[];

    //Constructor(s)
    /**
     * Constructs a game engine with default configurations
     */
    public GameEngine(UIManager uiManager, int numPlayer, String name) {
        this.gameDataManager = new GameDataManager();
        this.numPlayer = numPlayer;

        boolean isNewGame = (numPlayer != 0);

        if (!isNewGame) {
            this.gameData = gameDataManager.loadGame(name);
            level = gameData.getLevel();
            score = gameData.getScore();
            livesLeft = gameData.getLivesLeft();
            ghosts = gameData.getMapData().getGhosts();
            pacmans = gameData.getMapData().getPacmans();
            this.numPlayer = pacmans.length;
            this.numGhost = ghosts.length;
            gameMap = gameData.getMapData().getMapTable();
            boolean isMap = name.substring((name.length() - 3)).equals("map");

            if (isMap)
                for (int i = 0; i < 11; i++)
                    for (int j = 0; j < 20; j++)
                        if (gameMap[i][j] == -1)
                            gameMap[i][j] = 2;

        } else {
            level = 1;
            score = 0;
            livesLeft = MAX_LIFE;
            numGhost = 4;
            pacmans = new Pacman[numPlayer];
            pacmans[0] = new Pacman(PacmanType.MRPACMAN); //default pacman object for now
            if (numPlayer == 2) {
                pacmans[1] = new Pacman(PacmanType.MRSPACMAN);
            }

            ghosts = new Ghost[numGhost];
            ghosts[0] = new Ghost(GhostType.BLINKY);
            ghosts[1] = new Ghost(GhostType.PINKY);
            ghosts[2] = new Ghost(GhostType.INKY);
            ghosts[3] = new Ghost(GhostType.CLYDE);


            map = new GameMap();
            gameMap = map.map1;
        }

        //Create copies of gameMap, ghosts & pacmans in order to restore at the beginning of next level

        /* This restore for next level covers only scenarios of
         * new games and previously played games
         * It will give a problem on previously not played saved maps.
         *
         * TODO: add another flag for savedMap
         *
         * if(!isNewGame) {.. load from saved game...}
         * else if (savedmap) {..load from saved map... it is a new game, but a special map}
         * else {.. so this is a new game without a specified map, load default game}
         * if (isNewGame) {create initial map to restore from}
         */

        if(isNewGame) { //Create an initial map to restore each level
            gameMapRestore = new int[gameMap.length][gameMap[0].length];

            for (int i = 0; i < gameMap.length; i++)
                for (int j = 0; j < gameMap[0].length; j++)
                    gameMapRestore[i][j] = gameMap[i][j];

        }

        this.uiManager = uiManager;

        this.gamePanel = new GamePanel(gameMap, pacmans, ghosts);
        uiManager.add(gamePanel, Constants.GAME_PANEL);
        counter = 3.0;
        isPaused = false;
        pacmanMovementKeyBindings = new PacManMovementController(pacmans, (this.numPlayer == 2), gamePanel.getInputMap(WHEN_IN_FOCUSED_WINDOW), gamePanel.getActionMap());
        pacmanMovementKeyBindings.initMovementKeyBindings();

        pauseGameController = new PauseGameController(gamePanel.getInputMap(WHEN_IN_FOCUSED_WINDOW), gamePanel.getActionMap(), this);
        pauseGameController.initPauseKeyBindings();

        timeController = new TimeController(this, gameMap, gamePanel.foods, pacmans, (this.numPlayer == 2), ghosts, gamePanel);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                startGame();
            }
        });

    }

    //Methods

    public void startGame() {
        gamePanel.prepareGUI();
        gamePanel.updateLives(livesLeft);
        gamePanel.updateScore(score);
        timeController.startTimer();
    }

    private void reStartGame() {
        for (Ghost g : ghosts) {
            g.respawnInCage();
        }
        for (Pacman pm : pacmans) {
            pm.respawn();
        }
        timeController.startTimer();
    }

    /**
     * Constructs a GameData object with all of the attributes in
     * GameEngine. Calls DataLayer.GameDataBase.GameDataManager.setGameData()
     * function in order to save this GameData object into our database
     */
    public void saveGame(String saveName) {
        GameDataManager gameDataManager = new GameDataManager();
        gameDataManager.saveGameData(saveName + ".game", score, level, numPlayer, livesLeft, pacmans, ghosts, gameMap);
    }

    /**
     * Calls startCounter(). Countdown has now started. Continues
     * the game once startCounter returns.
     */
    public void resumeGame() {
        isPaused = false;
        timeController.startTimer();
        gamePanel.hidePausePanel();
        gamePanel.repaintRequest(gameMap);
        //TODO
    }

    public void pauseGame() {
        timeController.stopTimer();
        isPaused = true;
        gamePanel.showPausePanel();
        //TODO
    }

    public void passLevel() {
        if (level < 3) {
            level++;
            System.out.println("level passed");

            //Show shield panel
            timeController.stopTimer();
            isPaused = true;
            gamePanel.showShieldPanel();

        } else {
            gameOver();
            //TODO: Highscore - ask for user name
        }
    }

    public void setNextLevel(){
        //Deduct the payment of shield from score, if any
        if (pacmans[0].getShield() != null) {
                this.score -= pacmans[0].getShield().getCost();
        }

        //Reset map
        for (int i = 0; i < gameMap.length; i++)
            for (int j = 0; j < gameMap[0].length; j++)
                gameMap[i][j] = gameMapRestore[i][j];

        gamePanel.resetFood();

        //Reset Pacmans & ghosts
        for (Pacman p : pacmans)
            p.setForNextLevel();

        for (Ghost g : ghosts)
            g.setForNextLevel();

        isPaused = false;
        timeController.startTimer();
        gamePanel.hideShieldPanel();
        gamePanel.repaintRequest(gameMap);

    }


    public void gameOver() {
        this.uiManager.viewGameOver();
    }

    public void addScore(int scr) {
        score += scr;
        gamePanel.updateScore(score);
    }

    public int getScore() {return this.score;}

    public void pacmanDied() {
        livesLeft = livesLeft - 1;
        if (livesLeft == 0) {
            gameOver();
        } else {
            pacmans[0].setLivesLeft(pacmans[0].getLivesLeft() - 1);
            pacmans[0].setShield(null);
            if (numPlayer == 2){
                pacmans[1].setLivesLeft(pacmans[1].getLivesLeft() - 1);
                pacmans[1].setShield(null);
            }

            gamePanel.updateLives(livesLeft);
            reStartGame();
        }
    }

    public void setShield(Shield shield) {
        for(Pacman p : pacmans) {
            p.setShield(shield);
        }
    }
}
