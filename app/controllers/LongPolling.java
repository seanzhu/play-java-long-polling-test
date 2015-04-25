package controllers;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.pattern.AskTimeoutException;
import play.*;
import play.libs.Akka;
import play.libs.F;
import play.mvc.*;
import static akka.pattern.Patterns.ask;

import java.util.*;
import views.html.long_polling.display;

public class LongPolling extends Controller {

    private final static ActorRef messageActor =
            Akka.system().actorOf(Props.create(MessageActor.class), "binz");

    public static Result set(int key, int value) {
        messageActor.tell(new SetMessage(key, value), ActorRef.noSender());
        return ok("set(" + key + ", " + value + ")");
    }

    public static F.Promise<Result> polling(int key) {
        return F.Promise.wrap(ask(messageActor, new GetMessage(key), 10000)).map(
                new F.Function<Object, Result>() {
                    @Override
                    public Result apply(Object object) throws Throwable {
                        SetMessage setMessage = (SetMessage) object;
                        final int key = setMessage.getKey();
                        final int value = setMessage.getValue();
                        return ok("polled(" + key + ", " + value + ")");
                    }
                }
        ).recover(new F.Function<Throwable, Result>() {
            @Override
            public Result apply(Throwable throwable) throws Throwable {
                if (throwable instanceof AskTimeoutException) {
                    return status(NOT_MODIFIED);
                } else {
                    return internalServerError();
                }
            }
        });
    }

    public static Result display(Integer key) {
        Logger.info("display for key: " + key);
        return ok(display.render(key));
    }



    /**
     * define akka actor message
     */
    public static class SetMessage {
        private final int key;
        private final int value;

        public SetMessage(int key, int value) {
            this.key = key;
            this.value = value;
        }

        public int getKey() {
            return key;
        }

        public int getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "SetMessage{" +
                    "key=" + key +
                    ", value=" + value +
                    '}';
        }
    }

    public static class GetMessage {
        private final int key;

        public GetMessage(int key) {
            this.key = key;
        }

        public int getKey() {
            return key;
        }

        @Override
        public String toString() {
            return "GetMessage{" +
                    "key=" + key +
                    '}';
        }
    }

    /**
     * define message processing actor
     */
    public static class MessageActor extends UntypedActor {

        private Map<Integer, Set<ActorRef>> listener = new HashMap<Integer, Set<ActorRef>>();

        @Override
        public void onReceive(Object message) throws Exception {
            final String uuid = UUID.randomUUID().toString();
            Logger.info(uuid + " received: " + message);

            if (message instanceof SetMessage) {
                SetMessage setMessage = (SetMessage) message;
                final Set<ActorRef> actorRefs = listener.get(setMessage.getKey());

                if (actorRefs != null) {
                    for (ActorRef actorRef : actorRefs) {
                        actorRef.tell(setMessage, getSelf());
                        Logger.info("telling " + setMessage + " to " + actorRef);
                    }
                    listener.remove(setMessage.getKey());
                    Logger.info("removing actors for " + setMessage.getKey() + ", map=" + listener.toString());
                }
            } else if (message instanceof GetMessage) {
                GetMessage getMessage = (GetMessage) message;

                Set<ActorRef> actorRefs = listener.get(getMessage.getKey());
                if (actorRefs == null)
                    actorRefs = new HashSet<ActorRef>();
                actorRefs.add(getSender());
                listener.put(getMessage.getKey(), actorRefs);
                Logger.info(getSender() + "requests " + getMessage.getKey() + ", size of actorRefs=" + actorRefs.size() + ", map=" + listener.toString());
            } else {
                Logger.info("message unhandled");
                unhandled(message);
            }

            Logger.info(uuid + " processed: " + message);
        }
    }
}
