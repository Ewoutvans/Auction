package cloud.zeroprox.auction;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.item.inventory.ClickInventoryEvent;
import org.spongepowered.api.item.inventory.Container;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.InventoryArchetypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.property.InventoryDimension;
import org.spongepowered.api.item.inventory.property.InventoryTitle;
import org.spongepowered.api.item.inventory.property.SlotPos;
import org.spongepowered.api.item.inventory.query.QueryOperationTypes;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.service.economy.transaction.TransactionResult;
import org.spongepowered.api.service.pagination.PaginationList;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.TextTemplate;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.chat.ChatTypes;
import org.spongepowered.api.text.format.TextColors;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Plugin(id = "auction", name = "Auction", description = "Auction for players", url = "https://zeroprox.cloud", authors = {"ewoutvs_", "Alagild"})
public class Auction {

    @Inject
    private Logger logger;

    private int s_time = 120,
            s_min_start = 0,
            s_auc_fee = 0;
    private boolean s_can_cancel_with_items = false;

    private long lastAuctionTime = 0;
    private long auctionDelay = 30;
    private int timeLeft = s_time;
    private int count = 0;
    private Optional<UUID> auctionBidder = Optional.empty();
    private Optional<UUID> auctionStarter = Optional.empty();
    private Optional<ItemStack> auctionItem = Optional.empty();
    private int auctionBid = 0;
    private EconomyService economyService;
    private boolean needBroadcast = false;

    @Inject
    @DefaultConfig(sharedRoot = true)
    private Path defaultConfig;

    @Inject
    @DefaultConfig(sharedRoot = true)
    private ConfigurationLoader<CommentedConfigurationNode> configManager;

    private ConfigurationNode rootNode;

    CommandSpec startCmd = CommandSpec.builder()
            .description(Text.of("Start a auction with the minimum price"))
            .permission("auction.start")
            .arguments(GenericArguments.onlyOne(GenericArguments.integer(Text.of("starting bid"))))
            .executor((src, args) -> {
                if (!(src instanceof Player)) {
                    src.sendMessage(this.t_beplayer.apply().build());
                    return CommandResult.empty();
                }
                Player player = (Player) src;
                if (this.auctionStarter.isPresent() || this.auctionItem.isPresent()) {
                    src.sendMessage(this.t_auction_failactive.apply().build());
                    return CommandResult.empty();
                }
                long auctionLastDiff = (new Date().getTime() - this.lastAuctionTime) / 1000L;
                if (auctionLastDiff < this.auctionDelay) {
                    src.sendMessage(this.t_auction_faildelay.apply(ImmutableMap.of("seconds", (this.auctionDelay - auctionLastDiff))).build());
                    return CommandResult.empty();
                }
                Optional<ItemStack> itemStack = player.getItemInHand(HandTypes.MAIN_HAND);
                if (!itemStack.isPresent() || itemStack.get().isEmpty()) {
                    src.sendMessage(this.t_auction_failitem.apply().build());
                    return CommandResult.empty();
                }
                Optional<Integer> amount = args.getOne("starting bid");
                if (!amount.isPresent()) {
                    src.sendMessage(Text.of(TextColors.RED, "Starting bid has to be a real number"));
                    return CommandResult.empty();
                }
                if (amount.get() < this.s_min_start) {
                    src.sendMessage(this.t_auction_faillow.apply(ImmutableMap.of("min_start", this.s_min_start)).build());
                    return CommandResult.empty();
                }
                player.setItemInHand(HandTypes.MAIN_HAND, ItemStack.empty());
                player.sendMessage(this.t_auction_success.apply().build());
                this.timeLeft = s_time;
                this.lastAuctionTime = new Date().getTime();
                this.auctionStarter = Optional.of(player.getUniqueId());
                this.auctionItem = itemStack;
                this.count = 4;
                this.auctionBid = amount.get();
                return CommandResult.success();
            })
            .build();

    CommandSpec showCmd = CommandSpec.builder()
            .description(Text.of("Show current item"))
            .permission("auction.show")
            .executor((src, args) -> {
                if (!(src instanceof Player)) {
                    src.sendMessage(this.t_beplayer.apply().build());
                    return CommandResult.empty();
                }
                Player player = (Player) src;
                if (!this.auctionStarter.isPresent() || !this.auctionItem.isPresent()) {
                    src.sendMessage(this.t_auction_not_taking_place.apply().build());
                    return CommandResult.empty();
                }
                Inventory inventory = Inventory.builder()
                        .of(InventoryArchetypes.CHEST)
                        .property(InventoryDimension.of(9, 1))
                        .property(InventoryTitle.of(Text.of(TextColors.RED, "Auction item")))
                        .build(this);
                inventory.query(QueryOperationTypes.INVENTORY_PROPERTY.of(SlotPos.of(4, 0))).offer(this.auctionItem.get().copy());
                player.openInventory(inventory);
                return CommandResult.empty();
            })
            .build();

    CommandSpec cancelCmd = CommandSpec.builder()
            .description(Text.of("Cancel your auction"))
            .permission("auction.cancel")
            .executor((src, args) -> {
                if (!(src instanceof Player)) {
                    src.sendMessage(this.t_beplayer.apply().build());
                    return CommandResult.empty();
                }
                Player player = (Player) src;
                if (!this.auctionStarter.isPresent() || !this.auctionItem.isPresent()) {
                    src.sendMessage(this.t_auction_not_taking_place.apply().build());
                    return CommandResult.empty();
                }
                if (this.auctionStarter.get() != player.getUniqueId()) {
                    src.sendMessage(this.t_only_cancel_when_starter.apply().build());
                    return CommandResult.empty();
                }
                if (!this.s_can_cancel_with_items && this.auctionBidder.isPresent()) {
                    src.sendMessage(this.t_only_cancel_when_no_bids.apply().build());
                    return CommandResult.empty();
                }
                if (this.auctionBidder.isPresent()) {
                    Optional<UniqueAccount> oOpt = this.economyService.getOrCreateAccount(this.auctionBidder.get());
                    oOpt.ifPresent(uniqueAccount -> uniqueAccount.deposit(this.economyService.getDefaultCurrency(), BigDecimal.valueOf(this.auctionBid), Sponge.getCauseStackManager().getCurrentCause()));
                }
                this.needBroadcast = false;
                this.count = 0;
                this.auctionBidder = Optional.empty();
                this.auctionStarter = Optional.empty();
                player.getInventory().offer(this.auctionItem.orElse(ItemStack.empty()));
                this.auctionItem = Optional.empty();
                player.sendMessage(this.t_auction_cancelled.apply().build());
                return CommandResult.success();
            })
            .build();


    CommandSpec auctionCmd = CommandSpec.builder()
            .executor((src, args) -> {
                PaginationList.builder()
                        .title(Text.of(TextColors.GREEN, "Auction commands"))
                        .padding(Text.of(TextColors.GOLD, "="))
                        .contents(
                                Text.builder("/auc start ").color(TextColors.GREEN).append(Text.of(TextColors.WHITE, "<price>")).onClick(TextActions.suggestCommand("/auc start ")).build(),
                                Text.builder("/auc show").color(TextColors.GREEN).onClick(TextActions.suggestCommand("/auc show")).build(),
                                Text.builder("/auc cancel").color(TextColors.GREEN).onClick(TextActions.suggestCommand("/auc cancel")).build(),
                                Text.builder("/bid ").color(TextColors.GREEN).append(Text.of(TextColors.WHITE, "[price]")).onClick(TextActions.suggestCommand("/bid")).build())
                        .build()
                        .sendTo(src);

                return CommandResult.empty();
            })
            .description(Text.of("Auction plugin"))
            .child(startCmd, "start")
            .child(showCmd, "show")
            .child(cancelCmd, "cancel")
            .build();

    CommandSpec bidCmd = CommandSpec.builder()
            .permission("auction.bid")
            .arguments(GenericArguments.optional(GenericArguments.integer(Text.of("bid"))))
            .description(Text.of("Do a bid"))
            .executor((src, args) -> {
                if (!(src instanceof Player)) {
                    src.sendMessage(this.t_beplayer.apply().build());
                    return CommandResult.empty();
                }
                Player player = (Player) src;
                if (!this.auctionStarter.isPresent() || !this.auctionItem.isPresent()) {
                    src.sendMessage(this.t_auction_not_taking_place.apply().build());
                    return CommandResult.empty();
                }
                if (this.auctionStarter.get() == player.getUniqueId()) {
                    src.sendMessage(this.t_auctionbid_failbid.apply().build());
                    return CommandResult.empty();
                }
                if (this.auctionBidder.isPresent() && this.auctionBidder.get() == player.getUniqueId()) {
                    src.sendMessage(this.t_auctionbid_failbidder.apply().build());
                    return CommandResult.empty();
                }
                int newBid = args.<Integer>getOne(Text.of("bid")).orElse(this.auctionBid + 100);
                if (newBid <= this.auctionBid) {
                    src.sendMessage(this.t_auctionbid_failtoolow.apply().build());
                    return CommandResult.empty();
                }
                Optional<UniqueAccount> uOpt = this.economyService.getOrCreateAccount(player.getUniqueId());
                if (!uOpt.isPresent()) {
                    src.sendMessage(this.t_auctionbid_failmoney.apply().build());
                    return CommandResult.empty();
                }
                TransactionResult result = uOpt.get().withdraw(this.economyService.getDefaultCurrency(), BigDecimal.valueOf(newBid), Sponge.getCauseStackManager().getCurrentCause());
                if (result.getResult() != ResultType.SUCCESS) {
                    src.sendMessage(this.t_auctionbid_failmoney.apply().build());
                    return CommandResult.empty();
                }
                if (this.auctionBidder.isPresent()) {
                    Sponge.getServer().getPlayer(this.auctionBidder.get()).ifPresent(player1 -> player1.sendMessage(this.t_auctionbid_outbid.apply(ImmutableMap.of("price", newBid)).build()));
                    Optional<UniqueAccount> oOpt = this.economyService.getOrCreateAccount(this.auctionBidder.get());
                    oOpt.ifPresent(uniqueAccount -> uniqueAccount.deposit(this.economyService.getDefaultCurrency(), BigDecimal.valueOf(this.auctionBid), Sponge.getCauseStackManager().getCurrentCause()));
                }
                this.needBroadcast = true;
                this.count = 3;
                this.auctionBidder = Optional.of(player.getUniqueId());
                this.auctionBid = newBid;
                player.sendMessage(this.t_auctionbid_success.apply().build());
                return CommandResult.success();
            })
            .build();


    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        Sponge.getCommandManager().register(this, auctionCmd, "auction", "auc");
        Sponge.getCommandManager().register(this, bidCmd, "bid");

        Optional<EconomyService> serviceOpt = Sponge.getServiceManager().provide(EconomyService.class);
        if (!serviceOpt.isPresent()) {
            logger.error("No economy found");
            return;
        }
        economyService = serviceOpt.get();

        Task task = Task.builder()
                .execute(() -> {
                    if (!this.auctionStarter.isPresent()) return;

                    if ((this.timeLeft > 0 && this.timeLeft % 30 == 0)) {
                        this.count = 4;
                    }
                    if (this.needBroadcast || this.count != 0 || this.timeLeft <= 4) {
                        this.needBroadcast = false;
                        this.count--;
                        if (this.count <= 0) {
                            this.count = 0;
                        }
                        Sponge.getServer()
                                .getOnlinePlayers()
                                .forEach(player -> player.sendMessage(ChatTypes.ACTION_BAR,
                                        this.t_auction_message.apply(ImmutableMap.of(
                                                "starter", Sponge.getServer().getPlayer(this.auctionStarter.get()).get().getName(),
                                                "item", this.auctionItem.get().getQuantity() + "x " + this.auctionItem.get().getTranslation().get(player.getLocale()),
                                                "price", this.auctionBid,
                                                "time", this.timeLeft)
                                        ).build()));
                    }
                    if (this.timeLeft-- <= 0) {
                        if (this.auctionBidder.isPresent()) {
                            Optional<Player> bidder = Sponge.getServer().getPlayer(this.auctionBidder.get());
                            if (bidder.isPresent()) {
                                Sponge.getServer()
                                        .getOnlinePlayers()
                                        .forEach(player -> player.sendMessage(ChatTypes.ACTION_BAR,
                                                this.t_auction_sold.apply(ImmutableMap.of("price", this.auctionBid, "player", bidder.get().getName())).build()));
                                bidder.get().getInventory().offer(this.auctionItem.get().copy());
                                bidder.get().sendMessage(this.t_you_recieved.apply(ImmutableMap.of("item", this.auctionItem.get().getTranslation().get(bidder.get().getLocale()))).build());
                                this.economyService.getOrCreateAccount(this.auctionStarter.get()).ifPresent(uniqueAccount -> uniqueAccount.deposit(economyService.getDefaultCurrency(), BigDecimal.valueOf(this.auctionBid - this.s_auc_fee), Sponge.getCauseStackManager().getCurrentCause()));
                            } else {
                                Sponge.getServer()
                                        .getOnlinePlayers()
                                        .forEach(player -> player.sendMessage(ChatTypes.ACTION_BAR, this.t_auction_nobids.apply().build()));
                                Sponge.getServer().getPlayer(this.auctionStarter.get()).ifPresent(player -> player.getInventory().offer(this.auctionItem.get().copy()));
                                this.economyService.getOrCreateAccount(this.auctionBidder.get()).ifPresent(uniqueAccount -> uniqueAccount.deposit(economyService.getDefaultCurrency(), BigDecimal.valueOf(this.auctionBid), Sponge.getCauseStackManager().getCurrentCause()));
                            }
                        } else {
                            if (this.auctionStarter.isPresent()) {
                                Optional<Player> starter = Sponge.getServer().getPlayer(this.auctionStarter.get());
                                starter.ifPresent(player -> player.getInventory().offer(this.auctionItem.get().copy()));
                            }
                            Sponge.getServer()
                                    .getOnlinePlayers()
                                    .forEach(player -> player.sendMessage(ChatTypes.ACTION_BAR,this.t_auction_nobids.apply().build()));
                        }
                        this.auctionBid = 0;
                        this.auctionItem = Optional.empty();
                        this.auctionBidder = Optional.empty();
                        this.auctionStarter = Optional.empty();
                    }
                })
                .interval(1, TimeUnit.SECONDS)
                .name("Auction broadcaster")
                .submit(this);

        configManager = HoconConfigurationLoader.builder().setPath(defaultConfig).build();
        try {
            rootNode = configManager.load();
            loadConfig();
        } catch(IOException e) {
        } catch (ObjectMappingException e) {
            e.printStackTrace();
        }
    }

    @Listener
    public void onInventory(ClickInventoryEvent event, @First Player player) {
        if (player != null && player.getOpenInventory().isPresent()) {
            Optional<Container> container = event.getCause().first(Container.class);
            if (container.isPresent()) {
                Collection<InventoryTitle> titles = container.get().getProperties(InventoryTitle.class);
                for (InventoryTitle title : titles) {
                    if (title.getValue() == null) continue;
                    if (title.getValue().equals(Text.of(TextColors.RED, "Auction item")))
                        event.setCancelled(true);
                }
            }
        }
    }

    private void loadConfig() throws IOException, ObjectMappingException {
        if (rootNode.getNode("messages", "auction_failactive").isVirtual()) {
            logger.info("Creating configuration");

            rootNode.getNode("messages", "auction_failactive").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "There is already an item being auctioned right now. You have to wait until the auction ends before putting a new item up for auction."));
            rootNode.getNode("messages", "auction_faildelay").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "An auction closed recently. Another auction can be started in ", TextTemplate.arg("seconds").color(TextColors.YELLOW), TextColors.RED, " seconds."));
            rootNode.getNode("messages", "auction_failitem").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "You must have the item you wish to auction in your hand."));
            rootNode.getNode("messages", "auction_faillow").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "Minimum value to start is 0"));

            rootNode.getNode("messages", "auction_success").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.GREEN, "Your auction has begun!"));
            rootNode.getNode("messages", "auction_message").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.YELLOW, TextTemplate.arg("starter").color(TextColors.GREEN), " is auctioning ", TextTemplate.arg("item").color(TextColors.GREEN), TextColors.GOLD, " (info /auc show) price: ", TextTemplate.arg("price").color(TextColors.GREEN), TextColors.GOLD, " timeleft: ", TextTemplate.arg("time").color(TextColors.GREEN)));

            rootNode.getNode("messages", "auctionbid_failbid").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "You cannot bid on your own auctions."));
            rootNode.getNode("messages", "auctionbid_failbidder").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "Bid failed: You are already the highest bidder."));
            rootNode.getNode("messages", "auctionbid_failtoolow").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "Bid failed: That bid is too low. The current high bid is ", TextTemplate.arg("price")));
            rootNode.getNode("messages", "auctionbid_failmoney").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "Bid failed: you do not have that much money."));

            rootNode.getNode("messages", "auctionbid_success").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.GREEN, "Your bid was accepted! You are the new high bidder."));
            rootNode.getNode("messages", "auctionbid_outbid").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "You were outbid! The new high bid is ", TextTemplate.arg("price")));

            rootNode.getNode("messages", "auction_nobids").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "No bids - the item has been returned to the owner."));
            rootNode.getNode("messages", "auction_sold").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.GREEN, "Sold! The winning bid was ", TextTemplate.arg("price"), " by ", TextTemplate.arg("player")));
//            rootNode.getNode("messages", "auction_nospace").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "You won the auction but your inventory is full! Item delivery will be re-attempted in 60 seconds - if there are no free spaces in your inventory at that time, the item will be permanently lost."));
//            rootNode.getNode("messages", "auction_itemlost").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "No free space in inventory, item has been permanently lost. Sorry, no refunds!"));

            rootNode.getNode("messages", "beplayer").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "Be a player"));
            rootNode.getNode("messages", "auction_not_taking_place").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "No auction is taking place."));
            rootNode.getNode("messages", "auction_cancelled").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "Auction is cancelled"));
            rootNode.getNode("messages", "only_cancel_when_starter").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "You can only cancel if you are the starter of a auction."));
            rootNode.getNode("messages", "you_recieved").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.GREEN, "You received ", TextTemplate.arg("item")));

            configManager.save(rootNode);
            loadConfig();
        } else if (rootNode.getNode("settings", "time").isVirtual()) {
            rootNode.getNode("settings", "time").setValue(120);
            rootNode.getNode("settings", "min_start").setValue(0);
            rootNode.getNode("settings", "auc_fee").setValue(0);
            rootNode.getNode("settings", "can_cancel_with_bids").setValue(true);
            rootNode.getNode("messages", "only_cancel_when_no_bids").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "You can only cancel when there are no bids."));
            rootNode.getNode("messages", "auction_faillow").setValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextColors.RED, "Minimum value to start is ", TextTemplate.arg("min_start")));

            configManager.save(rootNode);
            loadConfig();
        } else {
            this.t_beplayer = rootNode.getNode("messages", "beplayer").getValue(TypeToken.of(TextTemplate.class));
            this.t_auction_not_taking_place = rootNode.getNode("messages", "auction_not_taking_place").getValue(TypeToken.of(TextTemplate.class));
            this.t_auction_cancelled = rootNode.getNode("messages", "auction_cancelled").getValue(TypeToken.of(TextTemplate.class));
            this.t_only_cancel_when_starter = rootNode.getNode("messages", "only_cancel_when_starter").getValue(TypeToken.of(TextTemplate.class));
            this.t_you_recieved = rootNode.getNode("messages", "you_recieved").getValue(TypeToken.of(TextTemplate.class));
            this.t_auction_failactive = rootNode.getNode("messages", "auction_failactive").getValue(TypeToken.of(TextTemplate.class));
            this.t_auction_faildelay = rootNode.getNode("messages", "auction_faildelay").getValue(TypeToken.of(TextTemplate.class));
            this.t_auction_failitem = rootNode.getNode("messages", "auction_failitem").getValue(TypeToken.of(TextTemplate.class));
            this.t_auction_success = rootNode.getNode("messages", "auction_success").getValue(TypeToken.of(TextTemplate.class));
            this.t_auction_message = rootNode.getNode("messages", "auction_message").getValue(TypeToken.of(TextTemplate.class));
            this.t_auctionbid_failbid = rootNode.getNode("messages", "auctionbid_failbid").getValue(TypeToken.of(TextTemplate.class));
            this.t_auctionbid_failbidder = rootNode.getNode("messages", "auctionbid_failbidder").getValue(TypeToken.of(TextTemplate.class));
            this.t_auctionbid_failtoolow = rootNode.getNode("messages", "auctionbid_failtoolow").getValue(TypeToken.of(TextTemplate.class));
            this.t_auctionbid_failmoney = rootNode.getNode("messages", "auctionbid_failmoney").getValue(TypeToken.of(TextTemplate.class));
            this.t_auctionbid_success = rootNode.getNode("messages", "auctionbid_success").getValue(TypeToken.of(TextTemplate.class));
            this.t_auctionbid_outbid = rootNode.getNode("messages", "auctionbid_outbid").getValue(TypeToken.of(TextTemplate.class));
            this.t_auction_nobids = rootNode.getNode("messages", "auction_nobids").getValue(TypeToken.of(TextTemplate.class));
            this.t_auction_sold = rootNode.getNode("messages", "auction_sold").getValue(TypeToken.of(TextTemplate.class));
            this.t_auction_faillow = rootNode.getNode("messages", "auction_faillow").getValue(TypeToken.of(TextTemplate.class));

            this.s_time = rootNode.getNode("settings", "time").getValue(TypeToken.of(Integer.class));
            this.s_min_start = rootNode.getNode("settings", "min_start").getValue(TypeToken.of(Integer.class));
            this.s_auc_fee = rootNode.getNode("settings", "auc_fee").getValue(TypeToken.of(Integer.class));
            this.s_can_cancel_with_items = rootNode.getNode("settings", "can_cancel_with_bids").getValue(TypeToken.of(Boolean.class));
            this.t_only_cancel_when_no_bids = rootNode.getNode("messages", "only_cancel_when_no_bids").getValue(TypeToken.of(TextTemplate.class));

        }
    }

    private TextTemplate t_beplayer,
            t_auction_not_taking_place,
            t_auction_cancelled,
            t_only_cancel_when_starter,
            t_you_recieved,
            t_auction_failactive,
            t_auction_faildelay,
            t_auction_failitem,
            t_auction_success,
            t_auction_message,
            t_auctionbid_failbid,
            t_auctionbid_failbidder,
            t_auctionbid_failtoolow,
            t_auctionbid_failmoney,
            t_auctionbid_success,
            t_auctionbid_outbid,
            t_auction_nobids,
            t_auction_sold,
            t_auction_faillow,
            t_only_cancel_when_no_bids;

}
