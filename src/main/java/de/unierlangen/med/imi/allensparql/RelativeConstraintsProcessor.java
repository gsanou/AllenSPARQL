/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.unierlangen.med.imi.allensparql;

import com.fasterxml.jackson.core.JsonParser;
import com.google.gson.JsonElement;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

/**
 *
 * @author matesn
 */
class RelativeConstraintsProcessor {

    String Allen = "";
    ArrayList<AllenStatement> allenStatements = new ArrayList<AllenStatement>();

    RelativeConstraintsProcessor(String Allen) {
        this.Allen = Allen;
        System.out.println("Processing relative temporal criteria for:\n" + Allen);
        String[] AllenStatementsInput = Allen.split("\n");
        //String[] AllenStatementsInput = Allen.split(";");
        for (int a = 0; a < AllenStatementsInput.length; a++) {
            System.out.println("Statement: " + AllenStatementsInput[a]);
            AllenStatement as = new AllenStatement(AllenStatementsInput[a]);
            allenStatements.add(as);
        }
    }

    private static String removeLastTwoCharacter(String str) {
        return str.substring(0, str.length() - 2);
    }

    String process() {

        String variables = "";
        String unknowns = "";
        String equationSystem = "";

        for (int a = 0; a < allenStatements.size(); a++) {

            AllenStatement as = allenStatements.get(a);
            as.toPreferred();

            if ((as.getInterval1().isDuration() || as.getInterval2().isDuration())) {
                if (!as.getRelation().contains(",")) {

                    String interval1 = as.getInterval1().getIntervallNameSPARQL();
                    String interval2 = as.getInterval2().getIntervallNameSPARQL();
                    String relation = as.getRelation();

                    String I1s = "unix_start_of_int_" + interval1;
                    String I1e = "unix_end_of_int_" + interval1;
                    String I2s = "unix_start_of_int_" + interval2;
                    String I2e = "unix_end_of_int_" + interval2;

                    variables += I1s + " = var('" + I1s + "')\n";
                    variables += I1e + " = var('" + I1e + "')\n";
                    variables += I2s + " = var('" + I2s + "')\n";
                    variables += I2e + " = var('" + I2e + "')\n";

                    if (as.getInterval1().isDuration()) {
                        equationSystem += I1e + " - " + I1s + " == " + as.getInterval1().getDurationSeconds() + "\n";
                        unknowns += I1s + "\n" + I1e + "\n";
                    }
                    if (as.getInterval2().isDuration()) {
                        equationSystem += I2e + " - " + I2s + " == " + as.getInterval2().getDurationSeconds() + "\n";
                        unknowns += I2s + "\n" + I2e + "\n";
                    }

                    System.out.println("--- " + interval1 + " " + relation + " " + interval2 + " ---\n");

                    equationSystem += I1s + " < " + I1e + "\n";
                    equationSystem += I2s + " < " + I2e + "\n";

                    switch (relation) {
                        case "before":
                            equationSystem += I1e + " < " + I2s + "\n";
                            break;
                        case "meets":
                            equationSystem += I1e + " == " + I2s + "\n";
                            break;
                        case "overlaps":
                            equationSystem += I1s + " < " + I2s + "\n";
                            equationSystem += I1e + " < " + I2e + "\n";
                            equationSystem += I1e + " > " + I2s + "\n";
                            break;
                        case "finished by":
                            equationSystem += I1s + " < " + I2s + "\n";
                            equationSystem += I1e + " == " + I2e + "\n";
                            break;
                        case "contains":
                            equationSystem += I1s + " < " + I2s + "\n";
                            equationSystem += I1e + " > " + I2e + "\n";
                            break;
                        case "starts":
                            equationSystem += I1s + " == " + I2s + "\n";
                            equationSystem += I1e + " < " + I2e + "\n";
                            break;
                        case "equals":
                            equationSystem += I1s + " == " + I2s + "\n";
                            equationSystem += I1e + " == " + I2e + "\n";
                            break;
                        default:
                            System.out.println("Error: RelativeConstraintProcessor does not know how to handle the relation '" + relation + "'!");
                            break;
                    }

                    System.out.println(equationSystem);
                    System.out.println("");
                }
            }
        }

        variables = cleanUp(variables).replaceAll("\n", "; ");
        equationSystem = removeLastTwoCharacter(cleanUp(equationSystem).replaceAll("\n", ", "));
        unknowns = removeLastTwoCharacter(cleanUp(unknowns).replace("\n", ", "));

        String returnResult = "";

        if (equationSystem.equals("")) {
            return "";
        }
        return (cleanUp(variables) + "solve([" + equationSystem + "], [" + unknowns + "])").replaceAll("\n", "");
    }

    private String cleanUp(String code) { // remove duplicate entries
        String[] lines = code.split("\n");
        Hashtable htable = new Hashtable();
        String result = "";
        for (int a = 0; a < lines.length; a++) {
            if (!htable.contains(lines[a]) || lines[a].trim().equals("") || lines[a].trim().startsWith("#")) {
                result += lines[a] + "\n";
                htable.put(a, lines[a]);
            }
        }
        return result.replaceAll("\n\n\n+", "\n\n");
    }

    String callSageMath(String query) {

        // Search cache first:
        File path = new File("cache");
        File[] files = path.listFiles();
        int numberOfFiles = files.length;
        for (int i = 0; i < files.length; i++) {
            if (files[i].isFile() && files[i].toString().contains(".key")) {
                //System.out.println(files[i]);
                try ( FileInputStream inputStream = new FileInputStream(files[i].toString())) {
                    String cacheString = IOUtils.toString(inputStream);
                    if (cacheString.equals(query)) {
                        try ( FileInputStream inputStream2 = new FileInputStream(files[i].toString().replaceAll(".key", ".value"))) {
                            String cacheString2 = IOUtils.toString(inputStream2);

                            return cacheString2;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (query.equals("")) {
            return "";
        }

        String sageCellURL = "https://sagecell.sagemath.org";
        String execString = "python sagecell-client.py " + sageCellURL + " \"" + query + "\"";
        String sageResult = "";
        String finalResult = "";
        String finalResult2 = "";

        PrintWriter writer3;
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                writer3 = new PrintWriter("callSageCell.bat", "UTF-8");
            } else {
                writer3 = new PrintWriter("callSageCell.sh", "UTF-8");
            }
            writer3.println(execString);
            writer3.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        System.out.println(execString);

        try {
            Process proc = null;
            if (System.getProperty("os.name").contains("Windows")) {
                proc = Runtime.getRuntime().exec(execString);
            } else {
                proc = Runtime.getRuntime().exec("sh callSageCell.sh");
            }

            String line = "";
            BufferedReader reader1 = new BufferedReader(new InputStreamReader(proc.getInputStream()));

            while ((line = reader1.readLine()) != null) {
                sageResult += line;
            }

            System.out.println("Result from SageCell (orig): " + sageResult + "\n");
            JSONObject json = new JSONObject(sageResult);
            System.out.println(json.toString(3));
            System.out.println("Result from SageCell (JSON): " + json + "\n");

            System.out.println("");

            Matcher m = Pattern.compile("\\[\\[(.*?)\\]\\]").matcher(sageResult);
            while (m.find()) {
                finalResult += m.group(1);
            }

            int inBrackets = 0;

            for (int a = 0; a < finalResult.length(); a++) {
                String c = "" + finalResult.charAt(a);
                if (c.equals("(")) {
                    inBrackets += 1;
                }
                if (c.equals(")")) {
                    inBrackets -= 1;
                }
                if (inBrackets == 0 && c.equals(",")) {
                    c = "\n";
                }
                finalResult2 += c;
            }

            //finalResult = finalResult.replaceAll(", ", "\n");
            System.out.println(finalResult2);

            while (proc.isAlive()) {
                proc.wait();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
        //timeStamp += "-" + ++numberOfFiles;

        try ( PrintWriter writer = new PrintWriter("cache/" + timeStamp + ".key", "UTF-8")) {
            writer.print(query);
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try ( PrintWriter writer = new PrintWriter("cache/" + timeStamp + ".value", "UTF-8")) {
            writer.print(finalResult2);
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return finalResult2;

    }

    String makeSparqlFilter(String relativeTemporalCriteria) {

        if (relativeTemporalCriteria.equals("")) {
            return "";
        }

        String[] tc = relativeTemporalCriteria.split("\n");
        String out = "";

        for (int a = 0; a < tc.length; a++) {
            String in = tc[a];

            if (!in.contains("<") && !in.contains("=") && !in.contains(">")) {
                in += " = 0";
            }

            if (in.toLowerCase().contains("year") || in.toLowerCase().contains("month") || in.toLowerCase().contains("week") || in.toLowerCase().contains("day")
                    || in.toLowerCase().contains("hour") || in.toLowerCase().contains("minute") || in.toLowerCase().contains("second")) {
            } else {
                out += "  FILTER (" + in.trim() + ") .\n";
            }
        }

        out = out.replaceAll("unix_start_of", "?unix_start_of");
        out = out.replaceAll("unix_end_of", "?unix_end_of");
        out = out.replaceAll("==", "=");

        return out;
    }
}
