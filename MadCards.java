import java.io.*;
import java.net.*;
import java.util.*;

public class MadCards {
    // Core Game Components
    private static final int MAX_PLAYERS = 4;
    private static ArrayList<Player> players = new ArrayList<>();
    private static ArrayList<Card> deck = new ArrayList<>();
    private static ArrayList<Card> discardPile = new ArrayList<>();
    private static Rules rules = new Rules();
    private static boolean winner = false;

    // Networking Components
    private static ArrayList<Socket> clientSockets = new ArrayList<>();
    private static ArrayList<PrintWriter> clientOutputs = new ArrayList<>();
    private static ArrayList<BufferedReader> clientInputs = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("Start as:\n1. Server\n2. Client");
        Scanner scanner = new Scanner(System.in);
        int choice = scanner.nextInt();

        if (choice == 1) {
            startServer();
        } else if(choice == 2) {
            startClient();
        } else {
            System.exit(0);
        }
    }

    // Server Logic
    public static void startServer() {
        int port = 12345;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Waiting for players...");

            // Wait for players to connect
            while (clientSockets.size() < MAX_PLAYERS) {
                Socket clientSocket = serverSocket.accept();
                clientSockets.add(clientSocket);
                clientOutputs.add(new PrintWriter(clientSocket.getOutputStream(), true));
                clientInputs.add(new BufferedReader(new InputStreamReader(clientSocket.getInputStream())));
                sendToClient(clientSockets.size() - 1, "Welcome to MadCards! Waiting for other players...");
                System.out.println("Player " + clientSockets.size() + " connected.");
            }

            System.out.println("All players connected. Starting game...");
            setUpGame();
            gameLoop();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void setUpGame() {
        // Create players
        for (int i = 0; i < clientSockets.size(); i++) {
            players.add(new Player("Player" + (i + 1), i));
        }

        // Create deck
        for (Card.CardColor color : Card.CardColor.values()) {
            for (int j = 0; j < 10; j++) {
                deck.add(new Card("Card" + j, j, color));
            }
        }
        Collections.shuffle(deck);

        // Distribute cards
        for (Player player : players) {
            for (int j = 0; j < 5; j++) {
                player.hand.add(deck.remove(0));
            }
        }

        rules.displayRules();
    }

    public static void gameLoop() {
        int currentPlayerIndex = 0;
    
        while (!winner) {
            Player currentPlayer = players.get(currentPlayerIndex);
    
            sendToClient(currentPlayerIndex, "Your turn! Top card: |" +
                (discardPile.isEmpty() ? "None" : discardPile.get(discardPile.size() - 1)) +
                "| Your hand: " + currentPlayer.hand.toString());
    
            boolean validMove = false;
    
            while (!validMove) {
                String action = receiveFromClient(currentPlayerIndex);
    
                if (action != null) {
                    if (action.startsWith("play")) {
                        try {
                            int cardIndex = Integer.parseInt(action.split(" ")[1]);
                            Card playedCard = currentPlayer.hand.get(cardIndex);
    
                            // Validate the card against the top card in the discard pile
                            Card topCard = discardPile.isEmpty() ? null : discardPile.get(discardPile.size() - 1);
                            if (topCard == null || playedCard.color == topCard.color || playedCard.cardNum == topCard.cardNum) {
                                currentPlayer.hand.remove(cardIndex);
                                discardPile.add(playedCard);
                                broadcast("Player " + (currentPlayerIndex + 1) + " played: " + playedCard);
                                validMove = true; // Move is valid, exit the loop
                            } else {
                                sendToClient(currentPlayerIndex, "Invalid move! Card must match color or number of the top card.");
                            }
                        } catch (Exception e) {
                            sendToClient(currentPlayerIndex, "Invalid input. Try again.");
                            
                        }
                    } else if (action.equals("draw")) {
                        if (!deck.isEmpty()) {
                            currentPlayer.hand.add(deck.remove(0));
                            sendToClient(currentPlayerIndex, "You drew a card.");
                            validMove = true; // Drawing ends the turn
                        } else {
                            sendToClient(currentPlayerIndex, "Deck is empty. You cannot draw.");
                            gameLoop();
                        }
                    } else {
                        sendToClient(currentPlayerIndex, "Invalid action. Try 'play <cardIndex>' or 'draw'.");
                        gameLoop();
                    }
                }
            }
    
            // Check for winner
            if (currentPlayer.hand.isEmpty()) {
                broadcast("Player " + (currentPlayerIndex + 1) + " has won!");
                winner = true;
                break;
            }
    
            // Handle chaos rules
            rules.madnessMeter++;
            if (rules.isChaosTriggered()) {
                String chaosRule = rules.addChaosRule();
                broadcast("CHAOS RULE ACTIVATED: " + chaosRule);
            }
    
            rules.displayRules();
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        }
    
        broadcast("Game over!");
    }
    

    // Client Logic
    public static void startClient() {
        String serverAddress = "localhost";
        int port = 12345;

        try (Socket socket = new Socket(serverAddress, port);
             PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in))) {

            String serverMessage;
            while ((serverMessage = input.readLine()) != null) {
                System.out.println("Server: " + serverMessage);

                if (serverMessage.startsWith("Your turn!")) {
                    System.out.println("Enter your move (e.g., play <cardIndex> or draw): ");
                    String move = consoleInput.readLine();
                    output.println(move);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Helper Methods for Networking
    private static void sendToClient(int clientIndex, String message) {
        clientOutputs.get(clientIndex).println(message);
    }

    private static String receiveFromClient(int clientIndex) {
        try {
            return clientInputs.get(clientIndex).readLine();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void broadcast(String message) {
        for (PrintWriter out : clientOutputs) {
            out.println(message);
        }
    }

    
    

    // Supporting Classes
    static class Player {
        String name;
        int id;
        ArrayList<Card> hand = new ArrayList<>();

        Player(String name, int id) {
            this.name = name;
            this.id = id;
        }
    }

    static class Card {
        String cardName;
        int cardNum;
        CardColor color;

        enum CardColor { BLUE, RED, GREEN, YELLOW }

        Card(String name, int num, CardColor color) {
            this.cardName = name;
            this.cardNum = num;
            this.color = color;
        }

        @Override
        public String toString() {
            return cardName + " (" + color + ")";
        }
    }

    static class Rules {
        int madnessMeter = 0;
        ArrayList<String> activeRules = new ArrayList<>();
        Random random = new Random();

        Rules() {
            activeRules.add("Play a card with the same color or number.");
            activeRules.add("If no valid card, draw from the deck.");
        }

        boolean isChaosTriggered() {
            return madnessMeter >= 5;
        }

        String addChaosRule() {
            String[] chaosRules = {
                    "Reverse play direction.",
                    "All players draw 2 cards.",
                    "Skip the next player's turn.",
                    "Change the active color to a random one.",
                    "Discard all cards of a random color."
            };
            String newRule = chaosRules[random.nextInt(chaosRules.length)];
            activeRules.add(newRule);
            return newRule;
        }

        void displayRules() {
            System.out.println("Current Rules:");
            for (String rule : activeRules) {
                System.out.println("- " + rule);
            }
        }
    }
}
