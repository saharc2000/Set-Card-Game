package bguspl.set.ex;

import bguspl.set.Env;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    
    /**
     * True if player notified the dealer that he has set for check
     */
    private volatile boolean isWoken=false;
    
    /**
     * lock for sychronization between dealer and player who sent set for checking
     */
    private Object lock;

    /**
     * The thread representing the dealer thread
     */
    public Thread dealerThread;

    /**
     * lock for sychronization between multiple players that sent set for checking
     */
    private Object lockForSendingSetToCheck = new ReentrantLock(true);

    /**
     * True if dealer is changing cards
     */
    private volatile boolean isChangingCards = false;

    /**
     * The time the dealer's thread need to sleep until updating timer
     */
    private final long sleepUntilUpdateTimer = 900;

    /**
     * The time the dealer's thread need to sleep until updating timer
     */
    private final long sleepUntilUpdateWarningTimer = 10;

        /**
     * The time the dealer's thread need to sleep until updating timer
     */
    Thread[] playersThreads;

    private Object endLock=new Object();

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        lock = new Object();
        dealerThread=Thread.currentThread();
        this.playersThreads = new Thread[players.length];
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        synchronized(endLock){
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        isChangingCards = true;
        for(int i=0;i<players.length;i++){
            playersThreads[i] = new Thread(players[i],"player"+players[i].id);
            playersThreads[i].start();
       }
    try{
        while (!shouldFinish()) {
            placeCardsOnTable();
            isChangingCards = false;
            updateTimerDisplay(true);
            timerLoop();
            isChangingCards = true;
            removeAllCardsFromTable();
        }
        if(terminate) throw new InterruptedException();
        isChangingCards = false;
        announceWinners();
    }
    catch(InterruptedException e){}
    for(int i=players.length-1;i>=0;i--){
            players[i].terminate();
            try{playersThreads[i].join();} catch(InterruptedException ignore){}
            synchronized(players[i].getLock()){}
        }
        endLock.notifyAll();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            int playerId = isLegalSetExist();
            if(playerId!=-1){
                isChangingCards = true;
                removeCardsFromTable(playerId);
                placeCardsOnTable();
                updateTimerDisplay(true);
                isChangingCards = false;
                synchronized(lock){
                lock.notify();
                }
            }
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate=true;
        dealerThread.interrupt();
        synchronized(endLock){
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks if any cards should be removed from the table and returns them to the deck.  
     */
    private void removeCardsFromTable(int playerId) {
        Queue<Integer> tokens = players[playerId].getOriginTokenQueue();
        for(int i=0;i<env.config.featureSize;i++){
            int slot = tokens.remove();
            table.removeCard(slot);
            table.playersTokens[slot].remove((Integer)playerId);
            LinkedList<Integer> ls = table.getPlayersTokens(slot);
            if(ls!=null && ls.size()>0){
                for(int pId: ls){
                    players[pId].getOriginTokenQueue().remove(slot);
                    table.playersTokens[slot].remove((Integer)pId);
                }
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     * @post - deck.deckSize = @pre(deckSize) - (number of null slots)
     */
    void placeCardsOnTable() {
        Integer[] slotToCard = table.slotToCard;
        final int size = env.config.deckSize;
        int cardId;
        int indexofC;
        for(int i=0;i<env.config.tableSize;i++){
            if(slotToCard[i]==null && deck.size()!=0){
                cardId = (int)(Math.random()*(size+1)); 
                while(!deck.contains(cardId)){
                    cardId = (int)(Math.random()*(size+1));
                }
                indexofC = deck.indexOf(cardId);
                table.placeCard(deck.remove(indexofC), i);
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private synchronized void sleepUntilWokenOrTimeout() {
        if(this.reshuffleTime - System.currentTimeMillis()>env.config.turnTimeoutWarningMillis){
            if(!isWoken){
                try{
                    this.wait(sleepUntilUpdateTimer);
                }
                catch(InterruptedException e){}
            }
        }
        else{
            try{
                this.wait(sleepUntilUpdateWarningTimer);
            }
            catch(InterruptedException e){}
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {     
        if(reset) {
            this.reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        }
        
        long timeLeft = this.reshuffleTime - System.currentTimeMillis();
        if(timeLeft<env.config.turnTimeoutWarningMillis & timeLeft>=0) env.ui.setCountdown(timeLeft, true);
        else if(timeLeft<0) env.ui.setCountdown(0, true);
        else env.ui.setCountdown(timeLeft, false);
    }

    /**
     * Returns all the cards from the table to the deck.
     * @post deck.size == @pre(deck.size) + (table.slotToCards.size)  
     */
    void removeAllCardsFromTable() {
        Integer[] slotToCard = table.slotToCard;
        for(Player p:players){
            p.clearQueuewithoutTokens();
        }

        env.ui.removeTokens();
        for(int i=0;i<slotToCard.length;i++){
            if (slotToCard[i]!=null){
                deck.add(slotToCard[i]);
                table.removeCard(i);
            }
            table.playersTokens[i].clear();
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int maxScore = -1;
        int winnersSize = 0;

        //chack what is the maximum score and how many players won
        for(Player p: players){
            if(maxScore < p.score()){
                maxScore = p.score();
                winnersSize = 1;
            }
            else if(maxScore == p.score()){
                winnersSize++;
            }
        }

        //create winnersId array
        int [] winners = new int [winnersSize];
        for(Player p: players){
            if(maxScore==p.score()){
                winners[winnersSize-1] = p.id;
                winnersSize--;
            }
        }
        env.ui.announceWinner(winners);
    }
    
    /**
     * Check if the set that the player sent is valid
     */
    private boolean isValidSet(int playerId){
        // find the cards on the table
        Integer[] slotToCard = table.slotToCard;
        BlockingQueue<Integer> setQ = players[playerId].getTokenQueue(); 
        if(setQ.size()==env.config.featureSize){
            Integer[] set = {slotToCard[setQ.remove()],slotToCard[setQ.remove()],slotToCard[setQ.remove()]};
            if(set[0]!=null & set[1]!=null & set[2]!=null){
                int[] setI = {set[0],set[1],set[2]};
                return env.util.testSet(setI);
            }
        }
        return false;
    } 

    /**
     * Check if any player has a set on the table (legal or ilegal)
     */
    private int isLegalSetExist(){
        if(isWoken){
            isWoken=false;
            int playerId = -1;
            long minLastTime = Long.MAX_VALUE;
            for(int i=0;i<players.length;i++){
                if(players[i].getLastTokenTime()!=-1 && minLastTime>players[i].getLastTokenTime()){
                    minLastTime = players[i].getLastTokenTime();
                    playerId = i;
                }
            }
            if(playerId!=-1){
                players[playerId].setLastTokenTime();
                if(isValidSet(playerId)){
                    players[playerId].setIsLegal();
                    return playerId;
                }
            }
            synchronized(lock){
                lock.notify();
            }
        }
        return -1;
    }

    /**
     * used by player to notify dealer he needs to check his set 
     */
    public void setIsWoken(){
        isWoken = true;
    }

    public Object getLock(){
        return this.lock;
    }

    public boolean getIsChangingCards(){
        return this.isChangingCards;
    }

    public Object getLockForSendingSetToCheck(){
        return lockForSendingSetToCheck;
    }

    public List <Integer> getDeck(){
        return this.deck;
    }
}
