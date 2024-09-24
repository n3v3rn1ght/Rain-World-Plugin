package com.nevernight.RainWorldPlugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.entity.EnderDragon;
import java.util.HashMap;
import org.bukkit.NamespacedKey;
import java.util.Map;
import java.util.Random;
import org.bukkit.event.player.PlayerTeleportEvent;

public class MyPaperPlugin extends JavaPlugin implements Listener {

    private int timer;
    private int initialTime;
    private BukkitRunnable timerTask;
    private BukkitRunnable lightningTask;
    private BukkitRunnable windChargeTask;
    private BukkitRunnable slowdownTask;
    private Random random = new Random();
    private boolean isStormActive = false;
    private Map<Player, Integer> lightningTimers = new HashMap<>();
    private Map<Player, Integer> surfaceTimers = new HashMap<>();
    public Map<Player, KarmaLevel> playerKarma = new HashMap<>(); // Карма для каждого игрока
    private Map<Player, Boolean> playerSlept = new HashMap<>(); // Отслеживание сна игроков

    public enum KarmaLevel {
        ЖЕСТОКОСТЬ(ChatColor.RED + "Ⓥ"),
        РАЗМНОЖЕНИЕ(ChatColor.LIGHT_PURPLE + "Ⓛ"),
        МАТЕРИАЛИЗМ(ChatColor.YELLOW + "Ⓒ"),
        ОБЖОРСТВО(ChatColor.GOLD + "Ⓖ"),
        САМОСОХРАНЕНИЕ(ChatColor.GREEN + "Ⓢ");

        private final String displayName;

        KarmaLevel(String displayName) {
            this.displayName = displayName;
        }

        public KarmaLevel getNext() {
            int ordinal = this.ordinal();
            if (ordinal == KarmaLevel.values().length - 1) return this; // Если это "САМОСОХРАНЕНИЕ", шаг вперед невозможен
            return KarmaLevel.values()[ordinal + 1];
        }

        public KarmaLevel getPrevious() {
            int ordinal = this.ordinal();
            if (ordinal == 0) return this; // Если это "ЖЕСТОКОСТЬ", шаг назад невозможен
            return KarmaLevel.values()[ordinal - 1];
        }

        public String getDisplayName() {
            return displayName;
        }
    }
	private Map<Player, Location> deathLocations = new HashMap<>();

private void loadPlayerKarma() {
    for (Player player : Bukkit.getOnlinePlayers()) {
        String playerUUID = player.getUniqueId().toString();
        String karmaName = getConfig().getString("karma." + playerUUID, KarmaLevel.ЖЕСТОКОСТЬ.name());
        KarmaLevel karmaLevel = KarmaLevel.valueOf(karmaName);
        playerKarma.put(player, karmaLevel);
    }
}

@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (command.getName().equalsIgnoreCase("home")) {
        if (sender instanceof Player) {
            Player player = (Player) sender;

            // Проверяем уровень кармы игрока
            KarmaLevel karmaLevel = playerKarma.get(player);
            if (karmaLevel == null) {
                player.sendMessage("Ваша карма не установлена.");
                return false;
            }

            if (karmaLevel == KarmaLevel.САМОСОХРАНЕНИЕ) {
                // Получаем местоположение кровати из данных игрока
                Location bedLocation = player.getBedSpawnLocation();

                if (bedLocation != null) {
                    // Телепортируем игрока к кровати
                    player.teleport(bedLocation);
                    player.sendMessage("Вы телепортированы к вашей кровати.");
                } else {
                    player.sendMessage("Вам не задано место спауна для кровати.");
                }
            } else {
                player.sendMessage(ChatColor.RED + "Требуется карма " + ChatColor.GREEN + "Ⓢ" + ChatColor.RED + " чтобы использовать эту команду.");
            }
            return true;
        } else {
            sender.sendMessage("Эту команду могут использовать только игроки.");
            return true; // чтобы метод завершился здесь, если отправитель не игрок
        }
    }

    // Проверка на OP для других команд
    if (!sender.isOp()) {
        sender.sendMessage(ChatColor.RED + "У вас недостаточно прав для использования этой команды.");
        return true;
    }

    if (command.getName().equalsIgnoreCase("endcycle")) {
        timer = 60; // Устанавливаем таймер на 1 минуту
        sender.sendMessage(ChatColor.GREEN + "Цикл будет завершен через 1 минуту.");
        return true;
    }

    if (command.getName().equalsIgnoreCase("newcycle")) {
        startNewCycle(); // Начинаем новый цикл
        sender.sendMessage(ChatColor.GREEN + "Новый цикл начался.");
        return true;
    }

    if (command.getName().equalsIgnoreCase("karmaup")) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Использование: /karmaup [игрок]");
            return false;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Игрок не найден.");
            return false;
        }

        KarmaLevel currentKarma = playerKarma.getOrDefault(target, KarmaLevel.ЖЕСТОКОСТЬ);
        if (currentKarma != KarmaLevel.САМОСОХРАНЕНИЕ) {
            KarmaLevel newKarma = currentKarma.getNext();
            playerKarma.put(target, newKarma);
            target.sendMessage(ChatColor.GREEN + "Ваша карма повышена до уровня: " + newKarma.getDisplayName());
            sender.sendMessage(ChatColor.GREEN + "Карма игрока " + target.getName() + " повышена до уровня: " + newKarma.getDisplayName());
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Карма игрока " + target.getName() + " уже на максимальном уровне.");
        }

        return true;
    }

    if (command.getName().equalsIgnoreCase("karmadown")) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Использование: /karmadown [игрок]");
            return false;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Игрок не найден.");
            return false;
        }

        KarmaLevel currentKarma = playerKarma.getOrDefault(target, KarmaLevel.ЖЕСТОКОСТЬ);
        if (currentKarma != KarmaLevel.ЖЕСТОКОСТЬ) {
            KarmaLevel newKarma = currentKarma.getPrevious();
            playerKarma.put(target, newKarma);
            target.sendMessage(ChatColor.RED + "Ваша карма понижена до уровня: " + newKarma.getDisplayName());
            sender.sendMessage(ChatColor.GREEN + "Карма игрока " + target.getName() + " понижена до уровня: " + newKarma.getDisplayName());
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Карма игрока " + target.getName() + " уже на минимальном уровне.");
        }

        return true;
    }

    return false;
}

private void savePlayerKarma() {
    for (Map.Entry<Player, KarmaLevel> entry : playerKarma.entrySet()) {
        String playerUUID = entry.getKey().getUniqueId().toString();
        getConfig().set("karma." + playerUUID, entry.getValue().name());
    }
    saveConfig();
}

@Override
public void onEnable() {
    getServer().getPluginManager().registerEvents(this, this);
    loadPlayerKarma(); // Загружаем карму из конфигурации
    startNewCycle();
	// Регистрация событий
    getServer().getPluginManager().registerEvents(this, this);

    // Регистрация ачивки
    NamespacedKey key = new NamespacedKey(this, "acceptance");
    String json = "{" +
        "\"criteria\": {" +
        "    \"dragon_kill\": {" +
        "        \"trigger\": \"minecraft:impossible\"" +
        "    }" +
        "}," +
        "\"display\": {" +
        "    \"icon\": {" +
        "        \"item\": \"minecraft:iron_sword\"," +
        "        \"id\": \"minecraft:iron_sword\"" +
        "    }," +
        "    \"title\": {" +
        "        \"text\": \"Принятие\"," +
        "        \"color\": \"red\"" +
        "    }," +
        "    \"description\": {" +
        "        \"text\": \"Последствия путешествия нагнали тебя\"" +
        "    }," +
        "    \"frame\": \"goal\"" +
        "}" +
        "}";


    Bukkit.getUnsafe().loadAdvancement(key, json);
}

@EventHandler
public void onDragonDeath(EntityDeathEvent event) {
    if (event.getEntity() instanceof EnderDragon) {
        EnderDragon dragon = (EnderDragon) event.getEntity();
        World world = dragon.getWorld();
        Location dragonLocation = dragon.getLocation();

        for (Player player : world.getPlayers()) {
            if (player.getLocation().distance(dragonLocation) <= 100) {
                // Выдаем ачивку
                NamespacedKey key = new NamespacedKey(this, "acceptance");
                Advancement advancement = Bukkit.getAdvancement(key);
                if (advancement != null) {
                    AdvancementProgress progress = player.getAdvancementProgress(advancement);
                    if (!progress.isDone()) {
                        for (String criteria : progress.getRemainingCriteria()) {
                            progress.awardCriteria(criteria);
                        }
                    }
                }

                // Устанавливаем карму на "ЖЕСТОКОСТЬ"
                playerKarma.put(player, KarmaLevel.ЖЕСТОКОСТЬ);
                player.sendMessage(ChatColor.RED + "Ваша карма была понижена до уровня: " + KarmaLevel.ЖЕСТОКОСТЬ.getDisplayName());
            }
        }
    }
}

@Override
public void onDisable() {
    savePlayerKarma(); // Сохраняем карму в конфигурацию
    cancelTasks();
}
@EventHandler
public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    String playerUUID = player.getUniqueId().toString();
    KarmaLevel karmaLevel = playerKarma.getOrDefault(player, KarmaLevel.ЖЕСТОКОСТЬ);
    getConfig().set("karma." + playerUUID, karmaLevel.name());
    saveConfig();
    playerKarma.remove(player); // Можно удалить данные о карме из памяти
}


private void startNewCycle() {
    savePlayerKarma(); // Сохраняем карму перед началом нового цикла
    initialTime = (6 + random.nextInt(11)) * 60; // Случайное время от 6 до 16 минут
    timer = initialTime;

    // Возвращаем всех мертвых игроков в игровой режим и к их кровати
    for (Player player : Bukkit.getOnlinePlayers()) {
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            Location bedLocation = player.getBedSpawnLocation(); // Получаем местоположение кровати игрока
            if (bedLocation == null) {
                bedLocation = player.getWorld().getSpawnLocation(); // Если кровать не установлена, телепортируем на спавн
            }

            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            player.teleport(bedLocation); // Телепортируем игрока к кровати или спавну
            player.sendMessage(ChatColor.GREEN + "Начался новый цикл.");
        }
    }

    startTimer();
    startSurfaceCheckTask(); // Запуск задачи для проверки нахождения игрока на поверхности
}

    private void updateActionBar() {
        StringBuilder progressBar = new StringBuilder();
        int completedMinutes = (initialTime - timer) / 60;

        for (int i = 0; i < completedMinutes; i++) {
            progressBar.append("○");
        }
        for (int i = completedMinutes; i < initialTime / 60; i++) {
            progressBar.append("⏺");
        }

        // Получение уровня кармы игрока
        for (Player player : Bukkit.getOnlinePlayers()) {
            KarmaLevel karmaLevel = playerKarma.getOrDefault(player, KarmaLevel.ЖЕСТОКОСТЬ); // Начальный уровень - ЖЕСТОКОСТЬ
            String message = ChatColor.GREEN + progressBar.toString() + ChatColor.WHITE + " | " + karmaLevel.getDisplayName();
            player.sendActionBar(message);
        }
    }

    private void startWindChargeSpawning() {
        if (windChargeTask != null) {
            windChargeTask.cancel();
        }

        windChargeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isStormActive) return;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (hasRoofAbove(player)) continue;

                    Location spawnLocation = player.getLocation().clone().add(
                            random.nextInt(11) - 5, 10, random.nextInt(11) - 5
                    );
                    spawnWindCharge(spawnLocation);
                }
            }
        };
        windChargeTask.runTaskTimer(this, 0, 20 * 5); // Спавн каждые 5 секунд за минуту до грозы
    }

    private void spawnWindCharge(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        EntityType windChargeType;
        try {
            windChargeType = EntityType.valueOf("breeze_wind_charge"); // Используйте корректный тип сущности
        } catch (IllegalArgumentException e) {
            getLogger().warning("EntityType 'breeze_wind_charge' не найден. Проверьте правильность типа.");
            return;
        }

        Entity windCharge = world.spawnEntity(location, windChargeType);

        // Ускорение падения заряда ветра в 4 раза
        Vector downward = new Vector(0, -0.4, 0);
        windCharge.setVelocity(downward);
    }

    private void startLightningSpawning() {
        if (lightningTask != null) {
            lightningTask.cancel();
        }

        lightningTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isStormActive) {
                    cancel();
                    return;
                }

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (hasRoofAbove(player)) {
                        lightningTimers.remove(player);
                        continue;
                    }

                    lightningTimers.putIfAbsent(player, 10); // Ставим таймер 10 секунд, если его нет

                    int timeLeft = lightningTimers.get(player);
                    timeLeft--;

                    if (timeLeft <= 0) {
                        Location spawnLocation = player.getLocation().clone().add(
                                random.nextInt(11) - 5, 0, random.nextInt(11) - 5
                        );
                        spawnLocation.setY(player.getWorld().getHighestBlockYAt(spawnLocation));

                        strikeLightning(spawnLocation);

                        lightningTimers.put(player, 10); // Сброс таймера
                    } else {
                        lightningTimers.put(player, timeLeft); // Обновляем таймер
                    }
                }
            }
        };
        lightningTask.runTaskTimer(this, 0, 20); // Проверка каждую секунду
    }

    private void strikeLightning(Location location) {
        World world = location.getWorld();
        if (world == null || !isStormActive) return;

        world.strikeLightning(location);
    }

private boolean hasRoofAbove(Player player) {
    Location location = player.getLocation();
    int height = location.getBlockY(); // Получаем высоту игрока

    // Проверяем высоту
    if (height < 60) {
        return true; // Если игрок ниже 60-ти блоков, считается, что у него есть крыша
    }

    // Проверяем наличие крыши над игроком
    for (int y = 1; y <= 5; y++) {
        if (location.clone().add(0, y, 0).getBlock().getType() != Material.AIR) {
            return true; // Если выше игрока есть блок, считаем, что есть крыша
        }
    }
    
    return false; // Если крыши нет
}
	
    private void startSurfaceCheckTask() {
        if (slowdownTask != null) {
            slowdownTask.cancel();
        }

        slowdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isStormActive || hasRoofAbove(player)) {
                        surfaceTimers.remove(player); // Сброс таймера, если игрок под крышей
                        player.removePotionEffect(PotionEffectType.SLOWNESS); // Снятие эффекта замедления
                        continue;
                    }

                    surfaceTimers.putIfAbsent(player, 10); // Установка таймера 10 секунд, если его нет

                    int timeLeft = surfaceTimers.get(player);
                    timeLeft--;

                    if (timeLeft <= 0) {
                        applySlowdownEffect(player);
                    } else {
                        surfaceTimers.put(player, timeLeft);
                    }
                }
            }
        };
        slowdownTask.runTaskTimer(this, 0, 20); // Проверка каждую секунду
    }

    private void applySlowdownEffect(Player player) {
        PotionEffect slowdown = new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 1, false, false);
        player.addPotionEffect(slowdown);
    }

    private void cancelTasks() {
        if (timerTask != null) {
            timerTask.cancel();
        }
        if (lightningTask != null) {
            lightningTask.cancel();
        }
        if (windChargeTask != null) {
            windChargeTask.cancel();
        }
        if (slowdownTask != null) {
            slowdownTask.cancel();
        }
        isStormActive = false;
    }

@EventHandler
public void onTimeSkip(TimeSkipEvent event) {
    World world = Bukkit.getWorlds().get(0);
    long time = world.getTime();

    if (time >= 0 && time < 12300) { // Проверяем, наступило ли утро
        boolean cycleStarted = false;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (playerSlept.getOrDefault(player, false)) {
                checkMorning(player); // Обновляем карму игрока
                cycleStarted = true;
            }
        }

        if (cycleStarted) {
            cancelTasks(); // Отменяем все задачи
            startNewCycle(); // Начинаем новый цикл
        }
    }
}

private void checkAllSpectatorsAndStartNewCycle() {
    boolean allSpectators = true;
    for (Player player : Bukkit.getOnlinePlayers()) {
        if (player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            allSpectators = false;
            break;
        }
    }

    if (allSpectators) {
        startNewCycle();
    }
}
@EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Проверяем, что игрок в режиме наблюдателя
        if (event.getPlayer().getGameMode().equals(org.bukkit.GameMode.SPECTATOR)) {
            // Отменяем событие телепортации
            event.setCancelled(true);
            // Сообщаем игроку, что телепортация запрещена
            event.getPlayer().sendMessage("Телепортация запрещена в режиме наблюдателя.");
        }
    }
@EventHandler
public void onPlayerDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();
    KarmaLevel currentKarma = playerKarma.getOrDefault(player, KarmaLevel.ЖЕСТОКОСТЬ);

    // Понижаем карму при смерти
    if (currentKarma != KarmaLevel.ЖЕСТОКОСТЬ) {
        KarmaLevel newKarma = currentKarma.getPrevious();
        playerKarma.put(player, newKarma);
    }

    // Сохраняем местоположение смерти
    Location deathLocation = player.getLocation();
    deathLocations.put(player, deathLocation);

    // Переводим игрока в режим наблюдателя на месте смерти
    new BukkitRunnable() {
        @Override
        public void run() {
            player.spigot().respawn(); // Принудительно респавним игрока
            player.teleport(deathLocation); // Телепортируем игрока на место смерти
            player.setInvisible(false);
            player.setCollidable(true);
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            player.sendMessage(ChatColor.RED + "Ожидание следующего цикла. Карма была понижена.");

            // Проверяем, все ли игроки в режиме наблюдателя
            checkAllSpectatorsAndStartNewCycle();
        }
    }.runTaskLater(this, 1L); // Перевод в режим наблюдателя через 1 тик
}

private void startTimer() {
    cancelTasks(); // Отмена всех текущих задач перед началом нового цикла

    timerTask = new BukkitRunnable() {
        @Override
        public void run() {
            timer--;
            updateActionBar();

            if (timer == 60) { // Начало последней минуты перед дождем
                startWindChargeSpawning();
            }

            if (timer == 0) {
                World world = Bukkit.getWorlds().get(0);
                world.setStorm(true);
                world.setThundering(true);
                isStormActive = true;

                // Запуск молний через 30 секунд после начала дождя
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (isStormActive) {
                            startLightningSpawning();
                        }
                    }
                }.runTaskLater(MyPaperPlugin.this, 20 * 30); // Запуск через 30 секунд

                // Проверяем, все ли игроки в режиме наблюдателя после окончания таймера
                checkAllSpectatorsAndStartNewCycle();

                cancel();
            }
        }
    };
    timerTask.runTaskTimer(this, 0, 20); // Обновление каждую секунду
}


@EventHandler
public void onPlayerBedEnter(PlayerBedEnterEvent event) {
    Player player = event.getPlayer();
    if (!playerSlept.getOrDefault(player, false)) {
        playerSlept.put(player, true); // Отмечаем, что игрок лег в кровать
    }
}

@EventHandler
public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
    Player player = event.getPlayer();
    World world = player.getWorld();
    long time = world.getTime();

    // Если игрок встал с кровати, и время 0, то повышаем карму и запускаем новый цикл
    if (time == 0 && playerSlept.getOrDefault(player, false)) {
        KarmaLevel currentKarma = playerKarma.getOrDefault(player, KarmaLevel.ЖЕСТОКОСТЬ);

        if (currentKarma != KarmaLevel.САМОСОХРАНЕНИЕ) {
            KarmaLevel newKarma = currentKarma.getNext();
            playerKarma.put(player, newKarma); // Повышаем карму
        }

        playerSlept.put(player, false); // Сбрасываем отметку о сне
        cancelTasks(); // Отменяем все задачи
        startNewCycle(); // Начинаем новый цикл
    }
}

private void checkMorning(Player player) {
    KarmaLevel currentKarma = playerKarma.getOrDefault(player, KarmaLevel.ЖЕСТОКОСТЬ);

    if (currentKarma != KarmaLevel.САМОСОХРАНЕНИЕ) {
        KarmaLevel newKarma = currentKarma.getNext();
        playerKarma.put(player, newKarma); // Повышаем карму при утреннем пробуждении
    }

    playerSlept.put(player, false); // Сброс отметки о сне
}
}
