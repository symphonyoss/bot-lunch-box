/*
 *
 *
 * Copyright 2016 Symphony Communication Services, LLC
 *
 * Licensed to Symphony Communication Services, LLC under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.symphonyoss.simplebot;

import com.jaunt.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.*;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.symphonyoss.client.SymphonyClient;
import org.symphonyoss.client.SymphonyClientFactory;
import org.symphonyoss.client.model.Room;
import org.symphonyoss.client.model.SymAuth;
import org.symphonyoss.client.services.RoomListener;
import org.symphonyoss.client.services.RoomMessage;
import org.symphonyoss.client.services.RoomService;
import org.symphonyoss.client.util.MlMessageParser;
import org.symphonyoss.symphony.agent.model.*;
import org.symphonyoss.symphony.clients.AuthorizationClient;
import org.symphonyoss.symphony.clients.DataFeedClient;
import org.symphonyoss.symphony.pod.model.IntegerList;
import org.symphonyoss.symphony.pod.model.Stream;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.symphonyoss.simplebot.LunchBoxBot.LunchBotCommand.*;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;


public class LunchBoxBot implements RoomListener {

    private final Logger logger = LoggerFactory.getLogger(LunchBoxBot.class);
    private SymphonyClient symClient;
    private Map<String, String> initParams = new HashMap<String, String>();
    private RoomService roomService;
    private Room lunchBoxRoom;
    private DataFeedClient dataFeedClient;
    private Datafeed datafeed;
    private String todayDateString;
    private Date todayDate;
    private boolean isFeedback;
    private String username;

    private ArrayList todayFoods = new ArrayList();

    static Set<String> initParamNames = new HashSet<String>();

    static {
        initParamNames.add("sessionauth.url");
        initParamNames.add("keyauth.url");
        initParamNames.add("pod.url");
        initParamNames.add("agent.url");
        initParamNames.add("truststore.file");
        initParamNames.add("truststore.password");
        initParamNames.add("keystore.password");
        initParamNames.add("certs.dir");
        initParamNames.add("bot.user.name");
        initParamNames.add("bot.user.email");
        initParamNames.add("room.stream");
    }

    public static void main(String[] args) throws ResponseException {
        new LunchBoxBot();
        System.exit(0);
    }

    public LunchBoxBot() throws ResponseException {
        DateFormat dateFormat = new SimpleDateFormat("yyyy,MM,dd");
        todayDate = new Date();
        todayDateString = dateFormat.format(todayDate);
        initParams();
        initAuth();
        initRoom();
        initDatafeed();
        listenDatafeed();
        //parseLunchMenu("2016,08,09");
    }

    private void initParams() {
        for (String initParam : initParamNames) {
            String systemProperty = System.getProperty(initParam);
            if (systemProperty == null) {
                throw new IllegalArgumentException("Cannot find system property; make sure you're using -D" + initParam + " to run LunchBoxBot");
            } else {
                initParams.put(initParam, systemProperty);
            }
        }
    }

    private void initAuth() {
        try {
            symClient = SymphonyClientFactory.getClient(SymphonyClientFactory.TYPE.BASIC);

            logger.debug("{} {}", System.getProperty("sessionauth.url"),
                    System.getProperty("keyauth.url"));


            AuthorizationClient authClient = new AuthorizationClient(
                    initParams.get("sessionauth.url"),
                    initParams.get("keyauth.url"));


            authClient.setKeystores(
                    initParams.get("truststore.file"),
                    initParams.get("truststore.password"),
                    initParams.get("certs.dir") + initParams.get("bot.user.name") + ".p12",
                    initParams.get("keystore.password"));

            SymAuth symAuth = authClient.authenticate();


            symClient.init(
                    symAuth,
                    initParams.get("bot.user.email"),
                    initParams.get("agent.url"),
                    initParams.get("pod.url")
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initRoom() {
        Stream stream = new Stream();
        stream.setId(initParams.get("room.stream"));

        try {
            roomService = new RoomService(symClient);

            lunchBoxRoom = new Room();
            lunchBoxRoom.setStream(stream);
            lunchBoxRoom.setId(stream.getId());
            lunchBoxRoom.setRoomListener(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void initDatafeed() {
        dataFeedClient = symClient.getDataFeedClient();
        try {
            datafeed = dataFeedClient.createDatafeed();
            sendMessage("Lunch is here! Type '/lunchbox help' to learn more\n");
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private MessageSubmission getMessage(String message) {
        MessageSubmission aMessage = new MessageSubmission();
        aMessage.setFormat(MessageSubmission.FormatEnum.TEXT);
        aMessage.setMessage(message);
        return aMessage;
    }

    private V2MessageSubmission getAttachmentMessage() {
        V2MessageSubmission message = new V2MessageSubmission();
        List<AttachmentInfo> attachments = new ArrayList();
        attachments.add(getAttachmentInfo());
        message.attachments(attachments);
        return message;
    }

    private AttachmentInfo getAttachmentInfo() {
        AttachmentInfo attachmentInfo = new AttachmentInfo();
        return attachmentInfo;
    }

    private void sendMessage(String message) {
        MessageSubmission messageSubmission = getMessage(message);
        try {
            symClient.getMessageService().sendMessage(lunchBoxRoom, messageSubmission);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void listenDatafeed() {
        while (true) {
            try {
                Thread.sleep(4000);
                MessageList messages = dataFeedClient.getMessagesFromDatafeed(datafeed);
                if (messages != null) {
                    for (Message m : messages) {
                        if (!m.getFromUserId().equals(symClient.getLocalUser().getId())) {
                            processMessage(m);
                        }
                    }
                }

            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private void processMessage(Message message) {
        username = message.getFromUserId().toString();
        String messageString = message.getMessage();
        if (StringUtils.isNotEmpty(messageString) && StringUtils.isNotBlank(messageString)) {
            MlMessageParser messageParser = new MlMessageParser();
            try {
                messageParser.parseMessage(messageString);
                String text = messageParser.getText();
                if (isFeedback == true) {
                    processFeedback(text);
                    isFeedback = false;
                }

                if (StringUtils.startsWithIgnoreCase(text, "/lunchbox")) {
                    LunchBotCommand cmd = getLunchBotCommand(text);

                    switch (cmd) {
                        case MENU:
                            HashMap lunch = parseLunchMenu(todayDateString);
                            sendMessage("#######"+lunch.get("title")+"#######"+"\n\n"+lunch.get("body"));
                            break;
                        case FEEDBACK:
                            isFeedback = true;
                            break;
                        case TOMORROW:
                            LocalDate tomorrowDate = LocalDate.from(todayDate.toInstant().atZone(ZoneId.of("UTC"))).plusDays(1);
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy,MM,dd");
                            String tomorrowString = tomorrowDate.format(formatter);
                            lunch = parseLunchMenu(tomorrowString);
                            sendMessage("#######"+lunch.get("title")+"#######"+"\n\n"+lunch.get("body"));
                            break;
                        case FORMAT:
                            sendMessage("- For feedback on individual items on the menu, <item number> -> <number of stars (out of 5)>\n- For feedback on lunch as a whole, <overall> -> <number of stars (out of 5)>, comments (optional)\n\n\nP.S: Please provide complete feedback in a single message with each section separated by a comma");
                            break;
                        case OPTIONS:
                            sendMessage("For today's menu:  '/lunchbox menu' OR '/lunchbox today's menu'\nFor tomorrow's menu: '/lunchbox tomorrow' OR '/lunchbox tomorrow's menu'\nFor tips on format for feedback: '/lunchbox format'\nFor feedback: '/lunchbox feedback' <hit enter and then provide feedback all in one message>\nFor options: '/lunchbox help'\n\n\nP.S: All commands should be typed without quotes");
                            break;
                        case UNKNOWN:
                            break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private LunchBotCommand getLunchBotCommand(String text) {
        if ((StringUtils.containsIgnoreCase(text, "menu") || StringUtils.containsIgnoreCase(text, "today's menu")) && !StringUtils.containsIgnoreCase(text, "tomorrow")) {
            return MENU;
        } else if (StringUtils.containsIgnoreCase(text, "feedback")) {
            return FEEDBACK;
        } else if (StringUtils.containsIgnoreCase(text, "tomorrow") || StringUtils.containsIgnoreCase(text, "tomorrow's menu")) {
            return TOMORROW;
        } else if (StringUtils.containsIgnoreCase(text, "format")) {
            return FORMAT;
        } else if (StringUtils.containsIgnoreCase(text, "help")) {
            return OPTIONS;
        } else {
            return UNKNOWN;
        }
    }


    @Override
    public void onRoomMessage(RoomMessage roomMessage) {

        Room room = roomService.getRoom(roomMessage.getId());

        if (room != null && roomMessage.getMessage() != null)
            logger.debug("New room message detected from room: {} on stream: {} from: {} message: {}",
                    room.getRoomDetail().getRoomAttributes().getName(),
                    roomMessage.getRoomStream().getId(),
                    roomMessage.getMessage().getFromUserId(),
                    roomMessage.getMessage().getMessage()

            );

    }

    public enum LunchBotCommand {
        MENU,
        FEEDBACK,
        TOMORROW,
        FORMAT,
        OPTIONS,
        UNKNOWN
    }

    private HashMap parseLunchMenu(String requestedDate) throws ResponseException {
        UserAgent userAgent = new UserAgent();
        System.out.println("SETTINGS:\n" + userAgent.settings);
        userAgent.visit("http://www.cater2.me/orders/shared/6137/13728,14838,14792");
        HashMap result = new HashMap();
        result.put("title", "This isn't the menu you are looking for");
        try {
            JNode timelineDict = userAgent.json.findFirst("timeline");
            JNode dateDict = timelineDict.findFirst("date");
            JNode ourEntry = null;
            for (JNode dateEntry : dateDict) {
                JNode startDate = dateEntry.get("startDate");
                String currentDate = startDate.toString();
                if (currentDate.substring(0, 10).compareTo(requestedDate) == 0) {
                    // this is the entry we need
                    ourEntry = dateEntry;
                    break;
                }
            }

            if (ourEntry != null) {
                JNode title = ourEntry.get("headline");
                String titleString = title.toString();
                String finalTitle = Jsoup.parse(titleString).text();
                finalTitle = removeTags(finalTitle);
                result.put("title", finalTitle);

                JNode meatOfBody = ourEntry.get("text");
                String meatOfBodyString = meatOfBody.toString();
                String duh = Jsoup.parse(meatOfBodyString).text();
                String cleanstring = removeTags(duh);

                String finalString = cleanstring.replaceAll("gdnesx","");
                finalString = finalString.replaceAll("gdnesab","");
                finalString = finalString.replaceAll("gdnesb","");
                finalString = finalString.replaceAll("gdesb","");
                finalString = finalString.replaceAll("gnesb","");
                finalString = finalString.replaceAll("gnesv","");
                finalString = finalString.replaceAll("gdneb","");
                finalString = finalString.replaceAll("gdne","");
                finalString = finalString.replaceAll("gdesx","");
                finalString = finalString.replaceAll("gesv","");
                finalString = finalString.replaceAll("gdnv","");
                finalString = finalString.replaceAll("dnesb","");
                finalString = finalString.replaceAll("dnex","");
                finalString = finalString.replaceAll("dnsx","");
                finalString = finalString.replaceAll("dnsv","");
                finalString = finalString.replaceAll("dns","");
                finalString = finalString.replaceAll("dnv","");
                finalString = finalString.replaceAll("nev","");
                finalString = finalString.replaceAll("dne","");
                finalString = finalString.replaceAll("Click here for full disclaimer.","");

                String[] components = finalString.split("\\\\n");

                boolean addFoodForToday = false;

                if (requestedDate.equals(todayDateString)) {
                    addFoodForToday = true;
                }

                boolean isHeading = false;
                boolean isText = false;
                int newLines = 0;
                int counter = 1;
                boolean detail = false;
                String finalizedFormattedString = new String();
                for (int i = 1; i < components.length; i++) {
                    finalizedFormattedString = finalizedFormattedString+"\n";
                    String temp = components[i];
                    if (temp.length() > 1) {
                        if (i == 1 || temp.equals(" Appetizer") || temp.equals(" Entrée") || temp.equals(" Side") || temp.equals(" Dessert") || temp.equals(" Sandwich") || temp.equals(" Salad") || temp.equals(" Dressing") || temp.equals(" Topping") || temp.equals(" Combo Meals") || temp.equals(" Sauce") || (isText && newLines == 8) || (detail && newLines == 3)){
                            // new section so more space for heading
                            isHeading = true;
                            isText = false;
                            detail = false;
                            finalizedFormattedString = finalizedFormattedString+"\n";
                            finalizedFormattedString = finalizedFormattedString + "-----"+temp+"------";
                            finalizedFormattedString = finalizedFormattedString+"\n";
                            newLines = 0;
                        }
                        else if (isHeading == true || (detail && newLines == 4) || (isText && newLines == 9)) {
                            // has to be text
                            if (addFoodForToday) {
                                todayFoods.add(temp);
                            }

                            finalizedFormattedString = finalizedFormattedString + counter + ". " + temp;
                            counter++;
                            isText = true;
                            isHeading = false;
                            detail = false;
                            newLines = 0;
                        }
                        else if (isText && newLines == 4)
                        {
                            // has to be subtitle/detail
                            finalizedFormattedString = finalizedFormattedString + "("+temp+")";
                            isHeading = false;
                            isText = false;
                            detail = true;
                            newLines = 0;
                        }
                    }
                    else {
                        // must be a newline delimiter
                        newLines++;
                    }
                }
                result.put("body", finalizedFormattedString);
            }
        } catch (NotFound notFound) {
            notFound.printStackTrace();
        }

        return  result;
    }


    private static final Pattern REMOVE_TAGS = Pattern.compile("<.+?>");

    public static String removeTags(String string) {
        if (string == null || string.length() == 0) {
            return string;
        }

        Matcher m = REMOVE_TAGS.matcher(string);
        return m.replaceAll("");
    }

    private void processFeedback(String feedback) throws ResponseException, IOException {
        if (todayFoods.isEmpty()) {
            // user has not explicitly seen the menu, so populate it
            parseLunchMenu(todayDateString);
        }

        HashMap entries = new HashMap();
        entries.put(0, username);

        String[] lines = feedback.split(",");
        for (String line : lines) {
            if (line.contains("-&gt;")) {
                String[] itemRatings = line.split("-&gt;");
                if (itemRatings[0].toLowerCase().contains("overall")) {
                    entries.put(todayFoods.size() + 1, itemRatings[1]);
                }
                else {
                    entries.put(Integer.parseInt(itemRatings[0].trim()), Integer.parseInt(itemRatings[1].trim()));
                }
            }
            else {
                String existing = (String)entries.get(todayFoods.size() + 2);
                if (existing != null) {
                    entries.put(todayFoods.size() + 2, existing + "\n" + line);
                }
                else {
                    entries.put(todayFoods.size() + 2, line);
                }
            }
        }

        try {
            writeToSpreadsheet(entries);
            isFeedback = false;
            sendMessage("Thank you for your feedback. Remember, you can always come back to change it within today. \nHere's a deformed pikachu for your efforts"+ "\n" +
                    "quu..__\n" +
                    " $$$b  `---.__\n" +
                    "  \"$$b        `--.                          ___.---uuudP\n" +
                    "   `$$b           `.__.------.__     __.---'      $$$$\"              .\n" +
                    "     \"$b          -'            `-.-'            $$$\"              .'|\n" +
                    "       \".                                       d$\"             _.'  |\n" +
                    "         `.   /                              ...\"             .'     |\n" +
                    "           `./                           ..::-'            _.'       |\n" +
                    "            /                         .:::-'            .-'         .'\n" +
                    "           :                          ::''\\          _.'            |\n" +
                    "          .' .-.             .-.           `.      .'               |\n" +
                    "          : /'$$|           .@\"$\\           `.   .'              _.-'\n" +
                    "         .'|$u$$|          |$$,$$|           |  <            _.-'\n" +
                    "         | `:$$:'          :$$$$$:           `.  `.       .-'\n" +
                    "         :                  `\"--'             |    `-.     \\\n" +
                    "        :##.       ==             .###.       `.      `.    `\\\n" +
                    "        |##:                      :###:        |        >     >\n" +
                    "        |#'     `..'`..'          `###'        x:      /     /\n" +
                    "         \\                                   xXX|     /    ./\n" +
                    "          \\                                xXXX'|    /   ./\n" +
                    "          /`-.                                  `.  /   /\n" +
                    "         :    `-  ...........,                   | /  .'\n" +
                    "         |         ``:::::::'       .            |<    `.\n" +
                    "         |             ```          |           x| \\ `.:``.\n" +
                    "         |                         .'    /'   xXX|  `:`M`M':.\n" +
                    "         |    |                    ;    /:' xXXX'|  -'MMMMM:'\n" +
                    "         `.  .'                   :    /:'       |-'MMMM.-'\n" +
                    "          |  |                   .'   /'        .'MMM.-'\n" +
                    "          `'`'                   :  ,'          |MMM<\n" +
                    "            |                     `'            |tbap\\\n" +
                    "             \\                                  :MM.-'\n" +
                    "              \\                 |              .''\n" +
                    "               \\.               `.            /\n" +
                    "                /     .:::::::.. :           /\n" +
                    "               |     .:::::::::::`.         /\n" +
                    "               |   .:::------------\\       /\n" +
                    "              /   .''               >::'  /");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeToSpreadsheet(HashMap feedbackMap) throws IOException {
        File file = new File("LunchFeedback"+todayDateString.replace(",", "-")+".xls");
        HSSFSheet spreadsheet = null;
        HSSFWorkbook workbook = null;
        if (!(file.isFile() && file.exists())) {
            file.createNewFile();
        }
        else {

            FileInputStream fis = new FileInputStream(file);
            workbook = new HSSFWorkbook(fis);
            spreadsheet = workbook.getSheetAt(0);
        }

        FileOutputStream fos = new FileOutputStream(file);

        if (spreadsheet == null) {
            // create a new spreadsheet with feedbackdata
            workbook = new HSSFWorkbook();
            spreadsheet = workbook.createSheet("Feedback");
            HSSFRow row0 = spreadsheet.createRow(0);
            HSSFRow row = spreadsheet.createRow(1);
            row0.createCell(0).setCellValue("User's ID");
            for (int i = 0; i < todayFoods.size(); i++) {
                row0.createCell(i+1).setCellValue((String) todayFoods.get(i));
            }

            row0.createCell(todayFoods.size() + 1).setCellValue("Overall");
            row0.createCell(todayFoods.size() + 2).setCellValue("Comments");
        }

        HSSFRow existingRow = null;
        for (int j=0; j< spreadsheet.getLastRowNum() + 1; j++) {
            HSSFRow row = spreadsheet.getRow(j);
            HSSFCell cell = row.getCell(0); //get first cell
            if (cell != null && cell.getStringCellValue().equals(username)) {
                // user has already submitted feedback, replace it with new feedback
                existingRow = row;
                break;
            }
        }

        if (existingRow == null) {
            existingRow = spreadsheet.createRow(spreadsheet.getLastRowNum() + 1);
        }

        // transfer feedback to spreadsheet
        Set <Integer> keySet = feedbackMap.keySet();
        for (Integer key : keySet ) {
            if (key == 0 || key == todayFoods.size() + 1 || key == todayFoods.size() + 2) {
                existingRow.createCell(key).setCellValue((String) feedbackMap.get(key));
            }
            else {
                existingRow.createCell(key).setCellValue((Integer) feedbackMap.get(key));
            }
        }

        workbook.write(fos);
        fos.close();
    }


}