package rama.npcintroductions;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NPCIntroductions extends JavaPlugin {

    public static NPCIntroductions plugin;
    private static File dataFile;
    private static FileConfiguration Data;
    public static BukkitTask talkTask;
    private Boolean PaPiHook = false;

    @Override
    public void onEnable() {
        plugin = this;
        new UpdateChecker(this, 105775).getVersion(version -> {
            if (this.getDescription().getVersion().equals(version)) {
                sendLog("&eYou are using the latest version.");
            } else {
                sendLog("&eThere is a new update available!");
                sendLog("&eYour current version: "+"&c"+plugin.getDescription().getVersion());
                sendLog("&eLatest version: "+"&a"+version);
            }
       });
        this.saveDefaultConfig();
        createDataFile();
        Bukkit.getPluginManager().registerEvents(new NPCListener(), this);
        TabExecutor tabExecutor = new Commands();
        this.getCommand("npci").setExecutor(tabExecutor);
        this.getCommand("npci").setTabCompleter(tabExecutor);
        if(getServer().getPluginManager().getPlugin("Citizens") == null){
            sendLog("Citizens dependency not found!");
        }
        initPaPi();

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static void sendLog(String message) {
        String prefix = ChatColor.translateAlternateColorCodes('&', "&c[&3NPCIntroductions&c] ");
        Bukkit.getConsoleSender().sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', message));
    }

    public static void reloadData() throws IOException {
        Data.save(dataFile);
        Data = YamlConfiguration.loadConfiguration(dataFile);
    }
    public static FileConfiguration getData(){
        return Data;
    }
    public void createDataFile(){
        dataFile = new File(getDataFolder(), "data.yml");
        if(!dataFile.exists()){
            dataFile.getParentFile().mkdirs();
            saveResource("data.yml", false);
        }
        Data = new YamlConfiguration();
        try{
            Data.load(dataFile);
        }catch(IOException | InvalidConfigurationException e){
            e.printStackTrace();
        }
    }
    public static void playIntroduction(int i, Player p){
        FileConfiguration config = plugin.getConfig();
        //variables
        String talk_type = config.getString("introductions."+i+".talk-type");
            //action
            List<String> action_commands = config.getStringList("introductions."+i+".action.command");
            List<String> action_commands_replaced = new ArrayList<>();
                for(String s : action_commands){
                    action_commands_replaced.add(s.replaceAll("%player%", p.getName()));
                }

            String action_type = config.getString("introductions."+i+".action.type");
            String action_sound_name = config.getString("introductions."+i+".action.sound");
            Sound action_sound = null;
            if(!action_sound_name.equalsIgnoreCase("NONE")) {
                action_sound = Sound.valueOf(action_sound_name);
            }
            long action_sound_pitch = config.getInt("introductions."+i+".action.sound-pitch");
            //action
        List<String> messages = config.getStringList("introductions."+i+".messages");
        String message_sound_name = config.getString("introductions."+i+".sound");
        Sound message_sound = null;
        if(!message_sound_name.equalsIgnoreCase("NONE")){
            message_sound = Sound.valueOf(message_sound_name);
        }

        long message_sound_pitch = config.getInt("introductions."+i+".sound-pitch");
        int interval = config.getInt("introductions."+i+".interval");

        List<String> uuids = getData().getStringList(String.valueOf(i));
        Boolean playerAlreadyClicked = uuids.contains(p.getUniqueId().toString());

        Boolean firstTime = config.getBoolean("introductions."+i+".first-time");
        //variables

        if(playerAlreadyClicked && firstTime){
            executeAction(action_type, p, action_commands_replaced, action_sound, action_sound_pitch, i);
            return;
        }

        Sound finalMessage_sound = message_sound;
        if(!talk_type.equalsIgnoreCase("NONE")){
            startNpcTalk(talk_type, p, interval, messages.size());
        }
        Sound finalAction_sound = action_sound;
        updateData(i, p);
        new BukkitRunnable() {
            int counter = 0;
            @Override
            public void run() {
                if (counter >= messages.size()) {
                    cancel();
                    executeAction(action_type, p, action_commands_replaced, finalAction_sound, action_sound_pitch, i);
                    return;
                }

                String message = plugin.colorized(messages.get(counter)).replaceAll("%player%", p.getName());

                if(plugin.getPaPiHook()){
                    message = PlaceholderAPI.setPlaceholders(p, message);
                }

                p.sendMessage(message);
                if(finalMessage_sound != null) {
                    p.playSound(p.getLocation(), finalMessage_sound, 100, message_sound_pitch);
                }
                counter++;
            }
        }.runTaskTimer(plugin, 0L, interval);
    }

    public static void updateData(int i, Player p){
        List<String> uuids = getData().getStringList(String.valueOf(i));
        if(getData().getStringList(String.valueOf(i)).contains(p.getUniqueId().toString())){
            return;
        }
        uuids.add(p.getUniqueId().toString());
        getData().set(String.valueOf(i), uuids);
        try {
            reloadData();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    public static void startNpcTalk(String talk_type, Player p, double interval, int size){
        //variables
        List<String> sounds = plugin.getConfig().getStringList("villager-talk."+talk_type+".sounds");
        long pitch = plugin.getConfig().getInt("villager-talk."+talk_type+".pitch");
        if(sounds == null){
            sendLog("&eCould not found "+talk_type+" talk type");
            return;
        }
        //variables
            interval = interval - 5;
        new BukkitRunnable() {
            int counter = 0;
            @Override
            public void run() {
                if(counter >= size){
                    cancel();
                    return;
                }
                Sound sound = Sound.valueOf(sounds.get(new Random().nextInt(sounds.size())));
                p.playSound(p.getLocation(), sound, 100, pitch);
                counter++;
            }
        }.runTaskTimerAsynchronously(plugin, 0, (long) interval);
    }
    public static void executeAction(String action_type, Player p, List<String> action_commands, Sound action_sound, long action_sound_pitch, int i){
        if(action_type.equalsIgnoreCase("PLAYER") && action_commands != null && !action_commands.isEmpty()){
            for(String command : action_commands) {
                String processedCommand = command;
                // Apply PlaceholderAPI if available
                if(plugin.getPaPiHook()){
                    processedCommand = PlaceholderAPI.setPlaceholders(p, processedCommand);
                }
                p.performCommand(processedCommand);
            }
            if(action_sound != null){
                p.playSound(p.getLocation(), action_sound, 100, action_sound_pitch);
            }
        }else if(action_type.equalsIgnoreCase("CONSOLE") && action_commands != null && !action_commands.isEmpty()){
            for(String command : action_commands) {
                String processedCommand = command;
                // Apply PlaceholderAPI if available
                if(plugin.getPaPiHook()){
                    processedCommand = PlaceholderAPI.setPlaceholders(p, processedCommand);
                }
                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
            }
            if(action_sound != null){
                p.playSound(p.getLocation(), action_sound, 100, action_sound_pitch);
            }
        }else{
            sendLog("&eUnrecognized action type for introduction "+i);
        }
    }

    private void initPaPi(){
        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null){
            sendLog("&aDetected PlaceholderAPI, enabling hook!");
            PaPiHook = true;
        }
    }

    public Boolean getPaPiHook() {
        return PaPiHook;
    }

    public String colorized(String s) {
        Pattern pattern = Pattern.compile("#[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(s);
        while (matcher.find()) {
            String hexCode = s.substring(matcher.start(), matcher.end());
            String replaceSharp = hexCode.replace('#', 'x');

            char[] ch = replaceSharp.toCharArray();
            StringBuilder builder = new StringBuilder();
            for (char c : ch) {
                builder.append("&").append(c);
            }

            s = s.replace(hexCode, builder.toString());
            matcher = pattern.matcher(s);
        }
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
