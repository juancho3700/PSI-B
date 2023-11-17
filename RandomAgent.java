import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.core.AID;
import jade.lang.acl.ACLMessage;


public class RandomAgent extends Agent {

    private State state;
    private AID mainAgent;
    private ACLMessage msg;


    protected void setup () {

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
    }


    protected void takeDown () {
        
        try {

            DFService.deregister (this);

        } catch (Exception e) {

            e.printStackTrace ();
        }

        System.exit (0);
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
                        }
                }
            }
        }


        private void validateSetupMessage (ACLMessage msg) throws NumberFormatException {

            String content = msg.getContent ();
            String [] contentSplit = content.split ("#");

            mainAgent = msg.getSender ();
            contentSplit = contentSplit [2].split (",");
        }


        private void validateNewGameMessage (ACLMessage msg) throws NumberFormatException {
            
            String content = msg.getContent ();
            String [] contentSplit = content.split ("#");
            contentSplit = contentSplit [1].split (",");
        }


        public void decidePlay () {

            ACLMessage msg = new ACLMessage (ACLMessage.INFORM);
            msg.addReceiver (mainAgent);
            int rint = (int) (Math.random () * 2);

            if (rint == 0) {

                msg.setContent ("Action#D");

            } else {

                msg.setContent ("Action#H");
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