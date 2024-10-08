# **Set Game Implementation**

## **Overview**

This implementation of the **Set** card game features a 3x4 grid of cards drawn from a deck. Players aim to identify "legal sets" of three cards based on the following features:

- **Color:** Red, Green, Purple
- **Number:** 1, 2, 3
- **Shape:** Squiggle, Diamond, Oval
- **Shading:** Solid, Partial, Empty

## **Objectives**

- **Concurrent Programming:** Implemented using Java threads and synchronization to handle concurrent gameplay and manage shared resources efficiently. This ensures that multiple players can interact with the game simultaneously without causing data inconsistencies or performance bottlenecks. The use of threads allows for real-time updates and responsiveness, which is crucial for a smooth gaming experience. Synchronization mechanisms, such as locks, are employed to coordinate access to shared game data and ensure that actions taken by one player are reflected accurately and in a timely manner across the entire game state.


## **Gameplay**

1. **Card Setup:**
   - 12 cards are dealt face up on a 3x4 grid.

2. **Finding Sets:**
   - Players place tokens on cards they think form a valid set.
   - When placing the third token, the dealer checks the set.

3. **Set Verification:**
   - **Legal Set:** Cards are removed, replaced with new ones, and the player earns a point. The player is then frozen briefly.
   - **Illegal Set:** The player is penalized and cannot place or remove tokens for a period.

4. **Card Replenishment:**
   - If no legal sets are available, the dealer reshuffles and deals new cards every minute.

5. **End of Game:**
   - The game ends when no legal sets remain. The player with the most points wins.

## **Implementation Details**

- **Threads:**
  - One per player and an additional one for non-human players.

- **Card Representation:**
  - Cards are numbered 0-80, with each having four features, each with three possible values.

- **Table:**
  - Manages the grid and token placements.

- **Players:**
  - Player threads manage token actions.
  - Non-human players are simulated by threads generating random actions.

- **Dealer:**
  - Manages game flow, including dealing, shuffling, and checking sets.
  - Ensures fair play and handles simultaneous set claims.

## **Game Flow**

- The dealer oversees the game, ensuring smooth operation and correct event logging.
- Threads are used efficiently, waking only when necessary.
