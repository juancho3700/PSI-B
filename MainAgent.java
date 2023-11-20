import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.io.PrintStream;
import java.util.ArrayList;


public class MainAgent extends Agent {
    
    ArrayList <PlayerInformation> players = new ArrayList <> ();
    private GUI gui;
    private AID [] playerAgentIds;
    private boolean stop = false;
    public GameParametersStruct params = new GameParametersStruct ();
    int gamesPlayed;


    @Override
    protected void setup () {

        gui = new GUI (this);
        System.setOut (new PrintStream (gui.getLoggingOutputStream ()));
        
        updatePlayers ();
        gui.logLine ("Agent " + getAID ().getName () + " ready");
    }


    public int updatePlayers() {

        gui.logLine ("Updating player list");
        
        int lastId = 0;
        DFAgentDescription template = new DFAgentDescription ();
        ServiceDescription sd = new ServiceDescription ();
        
        sd.setType ("Player");
        template.addServices (sd);
        
        try {

            DFAgentDescription [] result = DFService.search (this, template);
            
            if (result.length > 0) {
            
                gui.logLine ("Found " + result.length + " players");
            }

            playerAgentIds = new AID[result.length];
            
            for (int i = 0; i < result.length; ++i) {
            
                playerAgentIds [i] = result [i].getName ();
            }

        } catch (FIPAException fe) {
            
            gui.logLine (fe.getMessage ());
        }

        // Provisional
        String [] playerNames = new String [playerAgentIds.length];
        for (int i = 0; i < playerAgentIds.length; i++) {
            
            playerNames [i] = playerAgentIds [i].getName ();
        }
        
        gui.setPlayersUI (playerNames);
                
        for (AID aid : playerAgentIds) {
            
            players.add (new PlayerInformation (aid, lastId++));
        }
        
        changeNPlayers ();
        gui.updateParameters ();
        gui.addPlayers ();
        return 0;
    }


    public int newGame () {

        addBehaviour (new GameManager ());
        return 0;
    }


    
    private class GameManager extends SimpleBehaviour {
        
        int bestP = 0;

        public void action () {

            for (PlayerInformation player : players) {

                ACLMessage msg = new ACLMessage (ACLMessage.INFORM);
                msg.setContent ("Id#" + player.id + "#" + params.N + "," + params.R);
                msg.addReceiver (player.aid);
                send (msg);
            }

            System.out.println ("||||| NEW GAME FOR " + players.size () + " PLAYERS |||||");
            gamesPlayed = 0;

            for (int i = 0; i < players.size () - 1; i++) {
                for (int j = i + 1; j < players.size (); j++) {
                    
                    System.out.println ("New Round: " + i + " vs " + j);
                    playGame (players.get (i), players.get (j));
                    gamesPlayed ++;
                    gui.updateRounds ();
                }
            }

            for (int i = 1; i < players.size (); i++) {

                if (players.get (i).points > players.get (bestP).points) {

                    bestP = i;
                }
            }

            System.out.println ("\n\n\n**** WINNER ****");
            System.out.println ("*          " + players.get (bestP).aid.getName ().split ("@") [0] + "          *");
            System.out.println ("****************");

        }
        
        
        private void playGame (PlayerInformation p1, PlayerInformation p2) {
        
            ACLMessage msg = new ACLMessage (ACLMessage.INFORM);
            String pos1, pos2;
            
            int points1, points2, totalPoints1 = 0, totalPoints2 = 0;
            
            // Envia nueva partida a los jugadores
            
            msg.addReceiver (p1.aid);
            msg.addReceiver (p2.aid);
            msg.setContent ("NewGame#" + p1.id + "," + p2.id);
            send (msg);

            for (int i = 1; i <= params.R; i++) {
                
                while (stop);
                System.out.println ("\n\n---------- Round " + i + " ----------\n");
                
                // Envia peticion de jugada a p1 y printea la respuesta
    
                msg = new ACLMessage (ACLMessage.REQUEST);
                msg.setContent ("Action");
                msg.addReceiver (p1.aid);
                send (msg);
    
                ACLMessage movel = blockingReceive ();
                pos1 = movel.getContent ().split ("#") [1];
    
    
                // Envia peticion de jugada a p2 y printea la respuesta
    
                msg = new ACLMessage (ACLMessage.REQUEST);
                msg.setContent ("Action");
                msg.addReceiver (p2.aid);
                send (msg);
    
                movel = blockingReceive ();
                pos2 = movel.getContent ().split ("#") [1];
    
    
                // Envia el resultado de la ronda a los dos jugadores
    
                msg = new ACLMessage (ACLMessage.INFORM);
                msg.addReceiver (p1.aid);
                msg.addReceiver (p2.aid);
                
                if (pos1.equals ("D")) {
                    
                    if (pos2.equals ("D")) {
                        
                        points1 = 5;
                        points2 = 5;
                        
                    } else {
                        
                        points1 = 0;
                        points2 = 10;
                    }
    
                } else {
    
                    if (pos2.equals ("D")) {
                        
                        points1 = 10;
                        points2 = 0;
                        
                    } else {
                        
                        points1 = -1;
                        points2 = -1;
                    }
                }
                
                System.out.println ("Results#" + p1.id + "," + p2.id + "#" + pos1 + "," + pos2 + "#" + points1 + "," + points2);
                msg.setContent ("Results#" + p1.id + "," + p2.id + "#" + pos1 + "," + pos2 + "#" + points1 + "," + points2);
                send (msg);

                totalPoints1 += points1;
                totalPoints2 += points2;

                p1.addPoints (points1);
                p2.addPoints (points2);              

            }

            System.out.println ("\n\n****** RESULTS ******\n");

            msg = new ACLMessage (ACLMessage.INFORM);
            msg.addReceiver (p1.aid);            
            msg.addReceiver (p2.aid);

            System.out.println ("GameOver#" + p1.id + "," + p2.id + "#" + totalPoints1 + "," + totalPoints2);
            msg.setContent ("GameOver#" + p1.id + "," + p2.id + "#" + totalPoints1 + "," + totalPoints2);
            send (msg);

            if (totalPoints1 > totalPoints2) {

                gui.dfModel.addRow (new Object [] {p1.id, p2.id, totalPoints1, totalPoints2, p1.id});

            } else if (totalPoints1 < totalPoints2) {

                gui.dfModel.addRow (new Object [] {p1.id, p2.id, totalPoints1, totalPoints2, p2.id});

            } else {

                gui.dfModel.addRow (new Object [] {p1.id, p2.id, totalPoints1, totalPoints2, "Draw"});
            }

            gui.updatePlayers ();
        }
        

        @Override
        public boolean done () {
            
            return true;
        }
    }


    public void changeNPlayers () {

        params.N = players.size ();
        gui.updateParameters ();
    }


    public void changeRounds (int rounds) {

        params.R = rounds;
        gui.updateParameters ();
    }


    public void resetPlayers () {

        for (PlayerInformation player : players) {

            player.points = 0;
        }
    }


    public void stopExec () {

        System.out.println ("Paro antes: " + stop);
        stop = true;
        System.out.println ("Paro despues: " + stop);

    }


    public void continueExec () {

        System.out.println ("Sigo antes: " + stop);
        stop = false;
        System.out.println ("Sigo despues: " + stop);

    }


    public void removePlayer (String agent) {

        agent = agent.split ("@") [0];

        for (int i = 0; i < players.size (); i++) {

            if (agent.equals (players.get (i).aid.getName ().split ("@") [0])) {

                ACLMessage msgRm = new ACLMessage (ACLMessage.INFORM);
                msgRm.addReceiver (players.get (i).aid);
                msgRm.setContent ("Remove");
                send (msgRm);

                players.remove (i);
                gui.removePlayer (i);
                break;
            }
        }
    }



    public class PlayerInformation {

        AID aid;
        int id, points;


        public PlayerInformation (AID aid, int id) {

            this.aid = aid;
            this.id = id;
            points = 0;
        }
               
        public void addPoints (int p) {
        
            points += p;
        }

        public String toString () {

            return (aid.getName ().split ("@") [0] + " \t --- \t " + points + " points");
        }
    }



    public class GameParametersStruct {

        int N, R;


        public GameParametersStruct () {

            N = 0;
            R = 5;
        }
    }
}
