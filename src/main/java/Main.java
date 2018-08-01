import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;


public class Main {

    private static Logger logger =  LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        logger.trace("hello world...");
    }
}
