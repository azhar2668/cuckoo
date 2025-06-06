/*
    CuckooChess - A java chess program.
    Copyright (C) 2011  Peter Österlund, peterosterlund2@gmail.com

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.petero.cuckoo.uci;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Optional;

import org.petero.cuckoo.engine.chess.ChessParseError;
import org.petero.cuckoo.engine.chess.ComputerPlayer;
import org.petero.cuckoo.engine.chess.Move;
import org.petero.cuckoo.engine.chess.Position;
import org.petero.cuckoo.engine.chess.TextIO;

/**
 * Handle the UCI protocol mode.
 * @author petero
 */
public class UCIProtocol {
    // Data set by the "position" command.
    Position pos;
    final ArrayList<Move> moves;

    // Engine data
    EngineControl engine;

    // Set to true to break out of main loop
    boolean quit;


    public static void main(String[] args) {
    	boolean autostart = Boolean.parseBoolean(args[0]);
        UCIProtocol uciProt = new UCIProtocol();
        uciProt.mainLoop(System.in, System.out, autostart);
    }

    public UCIProtocol() {
        pos = null;
        moves = new ArrayList<>();
        quit = false;
    }

    public final void mainLoop(InputStream is, PrintStream os, boolean autoStart) {
        try {
            if (autoStart) {
                handleCommand("uci", os);
            }
            InputStreamReader inStrRead = new InputStreamReader(is);
            BufferedReader inBuf = new BufferedReader(inStrRead);
            String line;
            while ((line = inBuf.readLine()) != null) {
                handleCommand(line, os);
                if (quit) {
                    break;
                }
            }
        } catch (IOException ex) {
            // If stream closed or other I/O error, terminate program
        }
    }

    final void handleCommand(String cmdLine, PrintStream os) {
        String[] tokens = tokenize(cmdLine);
        try {
            String cmd = tokens[0];
            switch (cmd) {
                case "uci" -> {
                    os.printf("id name %s%n", ComputerPlayer.engineName);
                    os.printf("id author Peter Osterlund%n");
                    EngineControl.printOptions(os);
                    os.printf("uciok%n");
                }
                case "isready" -> {
                    initEngine(os);
                    os.printf("readyok%n");
                }
                case "setoption" -> {
                    initEngine(os);
                    StringBuilder optionName = new StringBuilder();
                    StringBuilder optionValue = new StringBuilder();
                    if (tokens[1].endsWith("name")) {
                        int idx = 2;
                        while ((idx < tokens.length) && !tokens[idx].equals("value")) {
                            optionName.append(tokens[idx++].toLowerCase());
                            optionName.append(' ');
                        }
                        if ((idx < tokens.length) && tokens[idx++].equals("value")) {
                            while ((idx < tokens.length)) {
                                optionValue.append(tokens[idx++].toLowerCase());
                                optionValue.append(' ');
                            }
                        }
                        engine.setOption(optionName.toString().trim(), optionValue.toString().trim());
                    }
                }
                case "ucinewgame" -> {
                    if (engine != null) {
                        engine.newGame();
                    }
                }
                case "position" -> {
                    String fen = null;
                    int idx = 1;
                    if (tokens[idx].equals("startpos")) {
                        idx++;
                        fen = TextIO.START_POS_FEN;
                    } else if (tokens[idx].equals("fen")) {
                        idx++;
                        StringBuilder sb = new StringBuilder();
                        while ((idx < tokens.length) && !tokens[idx].equals("moves")) {
                            sb.append(tokens[idx++]);
                            sb.append(' ');
                        }
                        fen = sb.toString().trim();
                    }
                    if (fen != null) {
                        pos = TextIO.readFEN(fen);
                        moves.clear();
                        if ((idx < tokens.length) && tokens[idx++].equals("moves")) {
                            for (int i = idx; i < tokens.length; i++) {
                                Optional<Move> m = TextIO.uciStringToMove(tokens[i]);
                                if (m.isPresent()) {
                                    moves.add(m.get());
                                } else {
                                    break;
                                }
                            }
                        }
                    }
                }
                case "go" -> {
                    if (pos == null) {
                        try {
                            pos = TextIO.readFEN(TextIO.START_POS_FEN);
                        } catch (ChessParseError ex) {
                            throw new RuntimeException();
                        }
                    }
                    initEngine(os);
                    int idx = 1;
                    SearchParams sPar = new SearchParams();
                    boolean ponder = false;
                    while (idx < tokens.length) {
                        String subCmd = tokens[idx++];
                        switch (subCmd) {
                            case "searchmoves" -> {
                                while (idx < tokens.length) {
                                    Optional<Move> m = TextIO.uciStringToMove(tokens[idx]);
                                    if (m.isPresent()) {
                                        sPar.searchMoves.add(m.get());
                                        idx++;
                                    } else {
                                        break;
                                    }
                                }
                            }
                            case "ponder" -> ponder = true;
                            case "wtime" -> sPar.wTime = Integer.parseInt(tokens[idx++]);
                            case "btime" -> sPar.bTime = Integer.parseInt(tokens[idx++]);
                            case "winc" -> sPar.wInc = Integer.parseInt(tokens[idx++]);
                            case "binc" -> sPar.bInc = Integer.parseInt(tokens[idx++]);
                            case "movestogo" -> sPar.movesToGo = Integer.parseInt(tokens[idx++]);
                            case "depth" -> sPar.depth = Integer.parseInt(tokens[idx++]);
                            case "nodes" -> sPar.nodes = Integer.parseInt(tokens[idx++]);
                            case "mate" -> sPar.mate = Integer.parseInt(tokens[idx++]);
                            case "movetime" -> sPar.moveTime = Integer.parseInt(tokens[idx++]);
                        }
                    }
                    if (ponder) {
                        engine.startPonder(pos, moves, sPar);
                    } else {
                        engine.startSearch(pos, moves, sPar);
                    }
                }
                case "stop" -> engine.stopSearch();
                case "ponderhit" -> engine.ponderHit();
                case "quit" -> {
                    if (engine != null) {
                        engine.stopSearch();
                    }
                    quit = true;
                }
            }
        } catch (ChessParseError | ArrayIndexOutOfBoundsException | NumberFormatException ignored) {
        }
    }

    private void initEngine(PrintStream os) {
        if (engine == null) {
            engine = new EngineControl(os);
        }
    }

    /** Convert a string to tokens by splitting at whitespace characters. */
    final String[] tokenize(String cmdLine) {
        cmdLine = cmdLine.trim();
        return cmdLine.split("\\s+");
    }
}
