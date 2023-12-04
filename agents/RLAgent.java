package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

public class RLAgent extends Agent {
    
    // Useamos Q-aprendizaje

    private State state;
    private AID mainAgent;
    private ACLMessage msg;

    private int lastAction = -1, lastReward = 0, playerId;
    private double probToPlay [] = {0.5, 0.5}, learnRate = 0.875, minLearnRate = 0.05;


    protected void setup () {

        System.out.println ("Setting up agent " + getAID ().getName ());

        state = State.s0NoConfig;

        DFAgentDescription dfd = new DFAgentDescription ();
        dfd.setName (getAID ());
        ServiceDescription sd = new ServiceDescription ();

        sd.setType ("Player");
        sd.setName ("Game");
        dfd.addServices (sd);

        try {

            DFService.register (this, dfd);

        } catch (Exception e) {

            e.printStackTrace ();
        }

        addBehaviour (new Play ());
        System.out.println ("Agent " + getAID ().getName () + " is ready");
    }


    protected void takeDown () {

        try {

            DFService.deregister (this);
            System.out.println ("Me mori");

        } catch (Exception e) {
            
            e.printStackTrace ();
        }
    }


    private enum State {

        s0NoConfig,
        s1AwaitingGame,
        s2Round,
        s3AwaitingResults
    }


    private class Play extends CyclicBehaviour {

        @Override
        public void action () {

            msg = blockingReceive ();

            if (msg != null) {

                switch (state) {

                    case s0NoConfig:

                        if (msg.getContent ().startsWith ("Id#") && msg.getPerformative () == ACLMessage.INFORM) {

                            try {

                                validateSetupMessage (msg);
                                state = State.s1AwaitingGame;

                            } catch (NumberFormatException e) {

                                System.out.println (getAID ().getName ().split ("@") [0] + ": " + state.name () + " - Bad message (" + msg.getContent () + ")");
                            }

                            break;

                        } else if (msg.getContent ().equals ("Remove")) {

                            takeDown ();
                            break;
                        }
                    
                        System.out.println (getAID ().getName ().split ("@") [0] + ": " + state.name () + " - Unexpected message (" + msg.getContent () + ")");
                        break;
                    
                    
                    case s1AwaitingGame:
                    
                        if (msg.getPerformative () == ACLMessage.INFORM) {

                            if (msg.getContent ().startsWith ("Id#")) {

                                try {

                                    validateSetupMessage (msg);

                                } catch (NumberFormatException e) {

                                    System.out.println (getAID ().getName ().split ("@") [0] + ": " + state.name () + " - Bad message (" + msg.getContent () + ")");
                                }

                                break;
                            
                            } else if (msg.getContent ().startsWith ("NewGame#")) {

                                try {
                                    
                                    validateNewGameMessage (msg);
                                    state = State.s2Round;

                                } catch (NumberFormatException e) {

                                    System.out.println (getAID ().getName ().split ("@") [0] + ": " + state.name () + " - Bad message (" + msg.getContent () + ")");
                                }

                                break;

                            } else if (msg.getContent ().equals ("Remove")) {

                                takeDown ();
                                break;
                            }
                        }

                        System.out.println (getAID ().getName ().split ("@") [0] + ": " + state.name () + " - Unexpected message (" + msg.getContent () + ")");
                        break;


                    case s2Round:

                        if (msg.getPerformative () == ACLMessage.INFORM && msg.getContent ().startsWith ("GameOver#")) {
                        
                            state = State.s1AwaitingGame;

                        } else if (msg.getPerformative () == ACLMessage.REQUEST && msg.getContent ().startsWith ("Action")) {

                            decidePlay ();
                            state = State.s3AwaitingResults;

                        } else {

                            System.out.println (getAID ().getName ().split ("@") [0] + ": " + state.name () + " - Bad message (" + msg.getContent () + ")");
                        }

                        break;


                    case s3AwaitingResults:

                        if (msg.getPerformative () == ACLMessage.INFORM && msg.getContent ().startsWith ("Results#")) {

                            state = State.s2Round;
                            getReward (msg);
                        }
                }
            }
        }

        
        private void validateSetupMessage (ACLMessage msg) throws NumberFormatException {
            
            String content = msg.getContent ();
            String [] contentSplit = content.split ("#");
            playerId = Integer.parseInt (contentSplit [1]);
            
            mainAgent = msg.getSender ();
            contentSplit = contentSplit [2].split (",");
        }
        
        
        private void validateNewGameMessage (ACLMessage msg) throws NumberFormatException {
            
            String content = msg.getContent ();
            String [] contentSplit = content.split ("#");
            contentSplit = contentSplit [1].split (",");
        }


        private void getReward (ACLMessage msg) {

            String split [] = msg.getContent ().split ("#");

            if (Integer.parseInt (split [1].split (",") [0]) == playerId) {

                lastReward = 0;

            } else {

                lastReward = 1;
            }
        }
        

        public void decidePlay () {

            ACLMessage msg = new ACLMessage (ACLMessage.INFORM);
            msg.addReceiver (mainAgent);
            
            if (lastAction >= 0 && lastReward > 0 && learnRate > 0) {

                for (int i = 0; i < 2; i++) {

                    if (i == lastReward) {

                        probToPlay [i] += learnRate * (1.0 - probToPlay [i]);

                    } else {

                        probToPlay [i] *= (1.0 - learnRate);

                    }
                }

                learnRate = Math.max (minLearnRate, learnRate * learnRate);
            }

            double action = Math.random ();

            if (action <= probToPlay [0]) {

                msg.setContent ("Action#D");
                lastAction = 0;

            } else {

                msg.setContent ("Action#H");
                lastAction = 1;
            }

            send (msg);
        }
    }



    public class PlayerInformation {

        int id, points = 0;

        public PlayerInformation (int id) {

            this.id = id;
        }

        public void addPoints (int added) {

            points += added;
        }
    }
}
