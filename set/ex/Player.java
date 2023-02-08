package bguspl.set.ex;

import java.util.logging.Level;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The slots of the player's tokens.
     */
    private BlockingQueue<Integer> tokens;

    /**
     * The time when the last token was placed.
     */
    private long lastTokenTime;

    private Dealer dealer;

    /**
     * The actions the player wants to do (slots he wants to place or remove token from).
     */
    private BlockingQueue<Integer> actionsQ;

    /**
     * The number of actions the player can perform at the same time.
     */
    private final int numOfActions = 3;

    /**
     * True if the player's set is correct according to dealer check
     */
    private boolean isLegal=false;
    
    /**
     * Lock for termination
     */
    private Object playerLock;
    
    /**
     * True when non human player enter a key to the action queue
     */
    private volatile boolean isWaitingForAction = false;

    /**
     * The time the player's thread need to sleep until updating the freeze time
     */
    private final int sleepUntilUpdateFreezeTime = 500;
    
    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.dealer=dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        this.tokens = new PriorityBlockingQueue<Integer>(env.config.featureSize);
        this.lastTokenTime =-1;
        this.actionsQ = new PriorityBlockingQueue<Integer>(numOfActions);
        this.playerLock = new Object();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        synchronized(playerLock){
            playerThread = Thread.currentThread();
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
            if (!human) createArtificialIntelligence();
            while (!terminate) {
                boolean isChanged = false;
                    try {
                    int slot;
                    if(!human && isWaitingForAction) {
                        synchronized(this){
                            slot = actionsQ.take();
                            if(dealer.getIsChangingCards()){
                                    actionsQ.clear();
                                    slot=-1;
                            }                    
                            isChanged = playerPress(slot);
                            isWaitingForAction = false;

                            if(tokens.size() == env.config.featureSize && isChanged){
                                threeTokensCheck();
                            }
                            this.notify();
                        } 
                    }
                    else if(human){
                        slot = actionsQ.take();
                        isChanged = playerPress(slot);
                        if(tokens.size() == env.config.featureSize && isChanged){
                            threeTokensCheck();
                        }
                    }
                }
                catch(InterruptedException ignored){}
            }
            if (!human) {
                aiThread.interrupt();
                try { aiThread.join(); } catch (InterruptedException ignored) {}
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
            playerLock.notify();
        }
    }

    private void threeTokensCheck(){    
        boolean sizeStillLegal=false;
        synchronized(dealer.getLockForSendingSetToCheck()){
            synchronized(dealer.getLock()){
                if(tokens.size()==env.config.featureSize){
                    sizeStillLegal=true;
                    lastTokenTime = System.currentTimeMillis();
                    dealer.setIsWoken();
                    synchronized(dealer){
                        dealer.notify();
                    }
                    try{
                        dealer.getLock().wait();
                    }
                    catch(InterruptedException e){}
                }
            }
            dealer.getLockForSendingSetToCheck().notify();
        }

        if(isLegal&sizeStillLegal){ 
            point(); 
            isLegal=false;
        }
        else if(sizeStillLegal) penalty();
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                try{
                    int tableSize = env.config.tableSize;
                    int slot = (int)(Math.random()*(tableSize));
                    if(keyPressed(slot)){
                        synchronized (this) {
                            isWaitingForAction = true;
                            this.wait(); 
                        }    
                    }
                }
                catch(InterruptedException ignore){}
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        this.terminate = true;
        playerThread.interrupt();
    }

    /**
     * This method is called when a key is pressed.
     * 
     * @param slot - the slot corresponding to the key pressed.
     * @return - true if action was performed, false otherwise
     *  
     */
    public boolean keyPressed(int slot) {
        if(dealer.getIsChangingCards()){
            actionsQ.clear();
            return false;
        }

        else{
            try{
                actionsQ.put(slot);
            }
            catch(InterruptedException ignore){}
            return true;
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        env.ui.setScore(id, ++score);
        freeze(env.config.pointFreezeMillis);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() { 
        if(env.config.hints) table.hints();
        freeze(env.config.penaltyFreezeMillis);
    }

    private void freeze(long freezeTime){
        long time = System.currentTimeMillis(); 
        while(freezeTime>0){
            long x = System.currentTimeMillis()-time;
            freezeTime = freezeTime-x;
            env.ui.setFreeze(id,freezeTime);
            time = System.currentTimeMillis();
            try{
                Thread.sleep(sleepUntilUpdateFreezeTime);
            }
            catch(InterruptedException e){}    
        }
        this.actionsQ.clear();
    }

    public BlockingQueue<Integer> getTokenQueue(){
        return new PriorityBlockingQueue<Integer>(tokens);
    }

    public BlockingQueue<Integer> getOriginTokenQueue(){
        return this.tokens;
    }

    public long getLastTokenTime(){
        return lastTokenTime;
    }

    public void setLastTokenTime(){
        lastTokenTime = -1;
    }

    /**
     * clear all player's tokens queue
     *
     * @post - tokens.size = 0
     */
    public void clearQueuewithoutTokens(){
        tokens.clear();
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - actionsQ.size == @pre(actionsQ.size) + 1 .
     */
    private boolean playerPress(int slot){
        if(slot!=-1 && tokens.contains(slot)){
            if(table.removeToken(this.id, slot)){
                tokens.remove(slot);
                table.playersTokens[slot].remove((Integer)id);
                return true;
            }
        }

        else if(slot!=-1 && tokens.size()<env.config.featureSize){
            if(table.placeToken(this.id, slot)){
                try{ tokens.put(slot);}
                catch(InterruptedException ignore){}
                table.playersTokens[slot].add((Integer)id);
                return true;
            }
        }
        return false;
    }

    public void setIsLegal(){
        this.isLegal=true;
    }

    public Object getLock(){
        return playerLock;
    }

    public BlockingQueue<Integer> getActionsQueue(){
        return this.actionsQ;
    }

    public int score() {
        return score;
    }
}
