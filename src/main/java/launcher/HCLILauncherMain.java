package launcher;

import schliph.CommandLineParser;

import java.util.Hashtable;

public class HCLILauncherMain {

    public static Hashtable<String, Object> args = new Hashtable<>();

    public static void main(String[] args) {

        try (CommandLineParser commandLineParser = new CommandLineParser(args)) {

            //TODO: жестко запарсить аргументы



        }




    }



}
