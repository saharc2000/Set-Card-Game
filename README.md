Set Game Implementation
Overview
This implementation is a version of the classic card game Set. The game starts with 12 cards drawn from a deck and arranged in a 3x4 grid. Players aim to find a "legal set" of three cards based on four features: color, number, shape, and shading. Each feature must be either all the same or all different across the three cards.

Features
Color: Red, Green, Purple
Number: 1, 2, 3
Shape: Squiggle, Diamond, Oval
Shading: Solid, Partial, Empty
Gameplay
Card Placement:

12 cards are dealt face up on a 3x4 grid.
Players place tokens on the cards they believe form a valid set.
Set Verification:

When a player places their third token, they ask the dealer to check the set.
Legal Set: If valid, the dealer removes the cards, replaces them with new ones, and awards a point. The player is then frozen for a short period.
Illegal Set: If invalid, the player faces a penalty and cannot place or remove tokens for a specified duration.
Card Replenishment:

If no legal sets are available, the dealer reshuffles and redeals cards every minute.
Game End:

The game continues until no legal sets are left in the remaining cards or deck.
The player with the most points at the end wins.
Implementation Details
Threads:

One thread per player and an additional thread for non-human players.
Threads are activated only when necessary (e.g., for key input or display updates).
Card Representation:

Cards are represented by integers 0-80.
Each card has four features, each with three possible values, creating a unique identifier.
Table:

Manages the 3x4 grid of cards and tracks token placements.
Shared resource between players and the dealer.
Players:

Player threads handle token placement and removal.
Non-human players are simulated by threads generating random actions.
Dealer:

Manages the overall game flow, including dealing, shuffling, and checking sets.
Handles synchronization to ensure fair processing of simultaneous set claims.
Game Flow
The dealer ensures smooth gameplay, handling all major tasks and event logging.
Threads are designed to wake only when required, optimizing performance and game efficiency.
