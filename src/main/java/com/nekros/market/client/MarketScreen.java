package com.nekros.market.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.nekros.market.economy.MarketEconomy;
import com.nekros.market.menu.MarketMenu;
import com.nekros.market.menu.MarketMenuEntry;
import com.nekros.market.menu.MarketMenuSnapshot;
import com.nekros.market.network.MarketAdminActionPayload;
import com.nekros.market.network.MarketActionPayload;
import com.nekros.market.system.SystemMarketOffer;
import com.nekros.market.util.InventoryUtil;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public class MarketScreen extends AbstractContainerScreen<MarketMenu> {
    private static final int MAX_MARKET_BUTTONS = 80;
    private static final int MAX_SYSTEM_BUTTONS = 256;
    private static final int MAX_CATEGORY_BUTTONS = 32;
    private final List<Button> buyButtons = new ArrayList<>();
    private final List<Integer> visibleMarketEntryIndexes = new ArrayList<>();
    private Button previousButton;
    private Button nextButton;
    private Button refreshButton;
    private Button listTabButton;
    private Button sellTabButton;
    private Button systemTabButton;
    private Button adminTabButton;
    private Button systemBuyTabButton;
    private Button systemSellTabButton;
    private Button submitSellButton;
    private Button confirmBuyButton;
    private Button cancelBuyButton;
    private Button adminAddBuyButton;
    private Button adminAddSellButton;
    private Button adminRemoveButton;
    private Button adminRemoveSellButton;
    private Button adminReloadButton;
    private Button adminAddCategoryButton;
    private Button adminRemoveCategoryButton;
    private final List<Button> systemTradeButtons = new ArrayList<>();
    private final List<Button> systemCategoryButtons = new ArrayList<>();
    private final List<Button> adminCategorySlotButtons = new ArrayList<>();
    private EditBox priceBox;
    private EditBox countBox;
    private EditBox buyCountBox;
    private EditBox adminIdBox;
    private EditBox adminItemBox;
    private EditBox adminPriceBox;
    private EditBox adminOfferCategoryBox;
    private EditBox adminCategoryNameBox;
    private MarketMenuEntry selectedBuyEntry;
    private SystemMarketOffer selectedSystemOffer;
    private boolean sellMode;
    private boolean systemMode;
    private boolean adminMode;
    private boolean systemSellPage;
    private String selectedSystemBuyCategory = firstSystemCategory();
    private int marketScrollRow;
    private int systemBuyScrollRow;
    private int systemSellScrollRow;
    private int adminItemScrollRow;
    private int draggingScrollbar;
    private int selectedAdminCategorySlot;
    private Item selectedAdminItem;
    private boolean adminSellOfferMode;
    private List<Item> adminAllItems;
    private List<Item> adminFilteredItems = List.of();
    private String adminLastSearch = "";
    private String adminStatus = "";
    private boolean updatingCountBox;
    private boolean updatingBuyCountBox;

    public MarketScreen(MarketMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 500;
        imageHeight = 300;
        inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        imageWidth = Math.min(500, Math.max(360, width - 40));
        imageHeight = Math.min(300, Math.max(230, height - 40));
        super.init();
        buyButtons.clear();
        systemTradeButtons.clear();
        systemCategoryButtons.clear();
        adminCategorySlotButtons.clear();

        for (int i = 0; i < MAX_MARKET_BUTTONS; i++) {
            final int row = i;
            Button button = Button.builder(Component.literal("Buy"), ignored -> buy(row))
                    .bounds(leftPos + 202 + (i % 2) * 236, topPos + 70 + (i / 2) * 42, 42, 16)
                    .build();
            buyButtons.add(addRenderableWidget(button));
        }

        listTabButton = addRenderableWidget(Button.builder(Component.literal("Market"), ignored -> setSellMode(false))
                .bounds(leftPos + 14, topPos + 274, 58, 16)
                .build());
        sellTabButton = addRenderableWidget(Button.builder(Component.literal("Sell"), ignored -> setSellMode(true))
                .bounds(leftPos + 78, topPos + 274, 38, 16)
                .build());
        systemTabButton = addRenderableWidget(Button.builder(Component.literal("System"), ignored -> setSystemMode(true))
                .bounds(leftPos + 122, topPos + 274, 56, 16)
                .build());
        adminTabButton = addRenderableWidget(Button.builder(Component.literal("Admin"), ignored -> setAdminMode(true))
                .bounds(leftPos + 184, topPos + 274, 48, 16)
                .build());
        systemBuyTabButton = addRenderableWidget(Button.builder(Component.literal("Buy"), ignored -> setSystemSellPage(false))
                .bounds(leftPos + 30, topPos + 36, 64, 18)
                .build());
        systemSellTabButton = addRenderableWidget(Button.builder(Component.literal("Sell"), ignored -> setSystemSellPage(true))
                .bounds(leftPos + 108, topPos + 36, 64, 18)
                .build());
        for (int i = 0; i < MAX_CATEGORY_BUTTONS; i++) {
            final int categoryIndex = i;
            Button button = Button.builder(Component.literal("#"), ignored -> selectSystemBuyCategory(categoryIndex))
                    .bounds(leftPos + 22, topPos + 72, 48, 17)
                    .build();
            systemCategoryButtons.add(addRenderableWidget(button));
        }

        previousButton = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> changePage(-1))
                .bounds(leftPos + 356, topPos + 274, 28, 16)
                .build());
        refreshButton = addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> refresh())
                .bounds(leftPos + 390, topPos + 274, 58, 16)
                .build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> changePage(1))
                .bounds(leftPos + 454, topPos + 274, 28, 16)
                .build());
        priceBox = addRenderableWidget(new EditBox(font, 0, 0, 96, 18, Component.literal("Price")));
        priceBox.setHint(Component.literal("Price"));
        priceBox.setMaxLength(18);
        priceBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));

        countBox = addRenderableWidget(new EditBox(font, 0, 0, 96, 18, Component.literal("Count")));
        countBox.setHint(Component.literal("Count"));
        countBox.setMaxLength(6);
        countBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        countBox.setResponder(ignored -> clampCountBox());

        buyCountBox = addRenderableWidget(new EditBox(font, 0, 0, 76, 18, Component.literal("Buy Count")));
        buyCountBox.setHint(Component.literal("1"));
        buyCountBox.setMaxLength(6);
        buyCountBox.setValue("1");
        buyCountBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        buyCountBox.setResponder(ignored -> clampBuyCountBox());

        adminIdBox = addRenderableWidget(new EditBox(font, 0, 0, 120, 18, Component.literal("Offer ID")));
        adminIdBox.setHint(Component.literal("id"));
        adminIdBox.setMaxLength(64);
        adminItemBox = addRenderableWidget(new EditBox(font, 0, 0, 150, 18, Component.literal("Item ID")));
        adminItemBox.setHint(Component.literal("Search item"));
        adminItemBox.setMaxLength(128);
        adminPriceBox = addRenderableWidget(new EditBox(font, 0, 0, 80, 18, Component.literal("Price")));
        adminPriceBox.setHint(Component.literal("price"));
        adminPriceBox.setMaxLength(18);
        adminPriceBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        adminOfferCategoryBox = addRenderableWidget(new EditBox(font, 0, 0, 80, 18, Component.literal("Offer Category")));
        adminOfferCategoryBox.setHint(Component.literal("#1"));
        adminOfferCategoryBox.setMaxLength(64);
        adminCategoryNameBox = addRenderableWidget(new EditBox(font, 0, 0, 90, 18, Component.literal("Category Name")));
        adminCategoryNameBox.setHint(Component.literal("Category name"));
        adminCategoryNameBox.setMaxLength(64);

        submitSellButton = addRenderableWidget(Button.builder(Component.literal("List Item"), ignored -> submitSell())
                .bounds(0, 0, 96, 18)
                .build());
        confirmBuyButton = addRenderableWidget(Button.builder(Component.literal("Confirm"), ignored -> confirmTrade())
                .bounds(0, 0, 82, 18)
                .build());
        cancelBuyButton = addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> closeConfirmation())
                .bounds(0, 0, 82, 18)
                .build());
        adminAddBuyButton = addRenderableWidget(Button.builder(Component.literal("Add Buy"), ignored -> submitAdminOffer("sell_to_player"))
                .bounds(0, 0, 70, 18)
                .build());
        adminAddSellButton = addRenderableWidget(Button.builder(Component.literal("Add Sell"), ignored -> submitAdminOffer("buy_from_player"))
                .bounds(0, 0, 70, 18)
                .build());
        adminRemoveButton = addRenderableWidget(Button.builder(Component.literal("Remove Buy"), ignored -> removeAdminOffer())
                .bounds(0, 0, 82, 18)
                .build());
        adminRemoveSellButton = addRenderableWidget(Button.builder(Component.literal("Remove Sell"), ignored -> removeAdminSellOffer())
                .bounds(0, 0, 86, 18)
                .build());
        adminReloadButton = addRenderableWidget(Button.builder(Component.literal("Reload Config"), ignored -> reloadAdminConfig())
                .bounds(0, 0, 100, 18)
                .build());
        adminAddCategoryButton = addRenderableWidget(Button.builder(Component.literal("Rename Cat"), ignored -> renameAdminCategory())
                .bounds(0, 0, 70, 18)
                .build());
        adminRemoveCategoryButton = addRenderableWidget(Button.builder(Component.literal("Reset Cat"), ignored -> resetAdminCategory())
                .bounds(0, 0, 86, 18)
                .build());
        for (int i = 0; i < 8; i++) {
            final int slot = i;
            Button button = Button.builder(Component.literal("#" + (i + 1)), ignored -> selectAdminCategorySlot(slot))
                    .bounds(0, 0, 34, 16)
                    .build();
            adminCategorySlotButtons.add(addRenderableWidget(button));
        }
        for (int i = 0; i < MAX_SYSTEM_BUTTONS; i++) {
            final int offerIndex = i;
            Button button = Button.builder(Component.literal("Buy"), ignored -> openSystemOffer(offerIndex))
                    .bounds(leftPos + 22, topPos + 62, 42, 14)
                    .build();
            systemTradeButtons.add(addRenderableWidget(button));
        }
        updateButtonStates();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateButtonStates();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderItemTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE60F1115);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xE6272B32);
        guiGraphics.fill(leftPos + 8, topPos + 24, leftPos + imageWidth - 8, topPos + 266, 0xCC15181D);

        MarketMenuSnapshot snapshot = menu.snapshot();
        guiGraphics.drawString(font, title, leftPos + 10, topPos + 9, 0xF4E7C5, false);
        guiGraphics.drawString(font, "Balance: " + snapshot.balance() + " " + MarketEconomy.CURRENCY_NAME, leftPos + 324, topPos + 9, 0xD6E8D2, false);
        if (sellMode) {
            renderSellPanel(guiGraphics);
            return;
        }
        if (systemMode) {
            renderSystemPanel(guiGraphics);
            return;
        }
        if (adminMode) {
            renderAdminPanel(guiGraphics);
            return;
        }

        List<MarketMenuEntry> entries = snapshot.entries();
        if (entries.isEmpty()) {
            guiGraphics.drawString(font, "No listings yet. Use /market sell <price> to list your held item.", leftPos + 18, topPos + 96, 0xB8C0CC, false);
            return;
        }

        visibleMarketEntryIndexes.clear();
        for (Button button : buyButtons) {
            button.visible = false;
            button.active = false;
        }

        int panelTop = topPos + 34;
        int panelBottom = topPos + 266;
        int rowHeight = 42;
        int visibleRows = Math.max(1, (panelBottom - panelTop) / rowHeight);
        int totalRows = Math.max(1, (entries.size() + 1) / 2);
        marketScrollRow = clampScrollRow(marketScrollRow, totalRows, visibleRows);

        int buttonIndex = 0;
        int startEntry = marketScrollRow * 2;
        int endEntry = Math.min(entries.size(), startEntry + visibleRows * 2);
        for (int i = startEntry; i < endEntry; i++) {
            int visibleIndex = i - startEntry;
            renderEntry(guiGraphics, entries.get(i), visibleIndex);
            if (buttonIndex < buyButtons.size()) {
                Button button = buyButtons.get(buttonIndex);
                int col = visibleIndex % 2;
                int row = visibleIndex / 2;
                button.setRectangle(42, 16, leftPos + 200 + col * 244, topPos + 43 + row * 42);
                button.visible = true;
                button.active = true;
                visibleMarketEntryIndexes.add(i);
                buttonIndex++;
            }
        }
        renderScrollbar(guiGraphics, leftPos + imageWidth - 14, panelTop, panelBottom, marketScrollRow, totalRows, visibleRows);
        if (selectedBuyEntry != null) {
            renderBuyConfirmation(guiGraphics);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!sellMode && !isConfirming() && isInsideContent(mouseX, mouseY)) {
            scrollActiveView(scrollY < 0.0D ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (adminMode && selectedAdminItem != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeAdminItemEditor();
            return true;
        }
        if (getFocused() instanceof EditBox editBox) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            editBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (getFocused() instanceof EditBox editBox && editBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && adminMode && !isConfirming()) {
            if (selectedAdminItem != null) {
                if (!isInsideAdminItemEditor(mouseX, mouseY)) {
                    closeAdminItemEditor();
                    return true;
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }
            if (selectAdminItemAt(mouseX, mouseY)) {
                return true;
            }
        }
        if (button == 0 && !sellMode && !isConfirming() && isInsideActiveScrollbar(mouseX, mouseY)) {
            draggingScrollbar = activeScrollbarTarget();
            setActiveScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingScrollbar != 0) {
            setActiveScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar != 0) {
            draggingScrollbar = 0;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void renderEntry(GuiGraphics guiGraphics, MarketMenuEntry entry, int row) {
        int col = row % 2;
        int cardRow = row / 2;
        int x = leftPos + 14 + col * 244;
        int y = topPos + 34 + cardRow * 42;
        int rowColor = row % 2 == 0 ? 0x4422272E : 0x442B3038;
        guiGraphics.fill(x, y, x + 234, y + 34, rowColor);
        guiGraphics.renderItem(entry.item(), x + 8, y + 8);
        guiGraphics.renderItemDecorations(font, entry.item(), x + 8, y + 8, Integer.toString(entry.count()));

        String itemName = trim(entry.item().getHoverName().getString(), 92);
        guiGraphics.drawString(font, itemName, x + 30, y + 5, 0xF3F0E8, false);
        guiGraphics.drawString(font, "Seller: " + trim(entry.sellerName(), 72), x + 30, y + 17, 0xAEB7C4, false);
        guiGraphics.drawString(font, entry.price() + " " + MarketEconomy.CURRENCY_NAME + " each", x + 115, y + 13, 0xD6E8D2, false);
        //guiGraphics.drawString(font, entry.shortId() + " | " + entry.count() + " left", x + 126, y + 17, 0xAEB7C4, false);
    }

    private void renderSellPanel(GuiGraphics guiGraphics) {
        clampCountBox();
        int panelX = leftPos + 18;
        int panelY = topPos + 72;
        int panelWidth = 464;
        int panelHeight = 178;

        int heldIconX = leftPos + 120;
        int heldIconY = topPos + 100;
        float heldIconScale = 3.0F;
        int heldInfoCenterX = leftPos + 144;
        int heldNameY = topPos + 165;
        int heldAvailableY = topPos + 190;

        int priceLabelX = leftPos + 350;
        int priceLabelY = topPos + 90;
        int priceBoxX = leftPos + 350;
        int priceBoxY = topPos + 104;
        int priceBoxWidth = 96;
        int priceBoxHeight = 18;
        int currencyTextX = leftPos + 350;
        int currencyTextY = topPos + 130;

        int countLabelX = leftPos + 350;
        int countLabelY = topPos + 170;
        int countBoxX = leftPos + 350;
        int countBoxY = topPos + 184;
        int countBoxWidth = 96;
        int countBoxHeight = 18;

        int submitButtonX = leftPos + 350;
        int submitButtonY = topPos + 214;
        int submitButtonWidth = 96;
        int submitButtonHeight = 18;

        if (priceBox != null) {
            priceBox.setRectangle(priceBoxWidth, priceBoxHeight, priceBoxX, priceBoxY);
        }
        if (countBox != null) {
            countBox.setRectangle(countBoxWidth, countBoxHeight, countBoxX, countBoxY);
        }
        if (submitSellButton != null) {
            submitSellButton.setRectangle(submitButtonWidth, submitButtonHeight, submitButtonX, submitButtonY);
        }

        guiGraphics.drawString(font, "Sell Held Item", leftPos + 18, topPos + 36, 0xF3F0E8, false);
        guiGraphics.drawString(font, "Put the item you want to sell in your main hand.", leftPos + 18, topPos + 52, 0xAEB7C4, false);

        ItemStack held = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getInventory().getSelected();
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x5522272E);
        if (held.isEmpty()) {
            guiGraphics.drawString(font, "Main hand is empty.", leftPos + 36, topPos + 106, 0xD68A8A, false);
        } else {
            renderScaledItem(guiGraphics, held, heldIconX, heldIconY, heldIconScale);
            drawCenteredStringAt(guiGraphics, trim(held.getHoverName().getString(), 128), heldInfoCenterX, heldNameY, 0xF3F0E8);
            drawCenteredStringAt(guiGraphics, "Available: " + maxSellCount(), heldInfoCenterX, heldAvailableY, 0xAEB7C4);
        }

        guiGraphics.drawString(font, "Unit price", priceLabelX, priceLabelY, 0xD9DEE8, false);
        guiGraphics.drawString(font, MarketEconomy.CURRENCY_NAME + " each", currencyTextX, currencyTextY, 0xAEB7C4, false);
        guiGraphics.drawString(font, "Count", countLabelX, countLabelY, 0xD9DEE8, false);
        guiGraphics.drawString(font, "Duration: 7 days. Listing fee disabled.", leftPos + 28, topPos + 235, 0xAEB7C4, false);
    }

    private void renderSystemPanel(GuiGraphics guiGraphics) {
        int panelX = leftPos + 14;
        int panelY = topPos + 55;
        int panelRight = leftPos + imageWidth - 14;
        int panelBottom = topPos + imageHeight - 34;
        int categoryColumnWidth = systemSellPage ? 0 : 72;
        int cardStartX = panelX + categoryColumnWidth + 25;
        int cardStartY = panelY + 15;
        int cardWidth = 60;
        int cardHeight = 85;
        int gap = 10;
        int buyTabX = leftPos + 44;
        int buyTabY = topPos + 36;
        int sellTabX = leftPos + imageWidth - 108;
        int sellTabY = topPos + 36;
        int tabWidth = 64;
        int tabHeight = 18;
        int categoryButtonX = leftPos + 28;
        int categoryButtonY = topPos + 66;
        int categoryButtonWidth = 60;
        int categoryButtonHeight = 20;
        int categoryButtonGap = 24;
        int tradeButtonWidth = 46;
        int tradeButtonHeight = 14;
        int tradeButtonBottomPadding = 17;
        int columns = Math.max(1, (panelRight - cardStartX - 10 + gap) / (cardWidth + gap));
        int rows = Math.max(1, (panelBottom - cardStartY - 8 + gap) / (cardHeight + gap));
        int visibleOfferCount = systemVisibleOfferCount();
        int totalRows = Math.max(1, (visibleOfferCount + columns - 1) / columns);
        systemBuyScrollRow = clampScrollRow(systemBuyScrollRow, totalRows, rows);
        int firstVisibleOffer = systemBuyScrollRow * columns;
        int lastVisibleOffer = firstVisibleOffer + rows * columns;
        List<SystemMarketOffer> systemOffers = SystemMarketOffer.offers();
        for (Button button : systemTradeButtons) {
            button.visible = false;
            button.active = false;
        }

        if (systemBuyTabButton != null) {
            systemBuyTabButton.setRectangle(tabWidth, tabHeight, buyTabX, buyTabY);
        }
        if (systemSellTabButton != null) {
            systemSellTabButton.setRectangle(tabWidth, tabHeight, sellTabX, sellTabY);
        }
        for (int i = 0; i < systemCategoryButtons.size(); i++) {
            systemCategoryButtons.get(i).setRectangle(categoryButtonWidth, categoryButtonHeight,
                    categoryButtonX, categoryButtonY + i * categoryButtonGap);
        }

        guiGraphics.fill(panelX, panelY, panelRight, panelBottom, 0xCC15181D);

        if (selectedSystemOffer != null) {
            renderBuyConfirmation(guiGraphics);
            return;
        }

        if (systemSellPage) {
            renderSystemSellPanel(guiGraphics, panelX, panelY, panelRight, panelBottom, cardWidth, cardHeight, gap,
                    tradeButtonWidth, tradeButtonHeight, tradeButtonBottomPadding);
            return;
        }

        int visibleIndex = 0;
        for (int i = 0; i < systemOffers.size(); i++) {
            SystemMarketOffer offer = systemOffers.get(i);
            if (!isSystemOfferVisible(offer)) {
                continue;
            }
            if (visibleIndex < firstVisibleOffer) {
                visibleIndex++;
                continue;
            }
            if (visibleIndex >= lastVisibleOffer) {
                break;
            }
            int pageIndex = visibleIndex - firstVisibleOffer;
            int col = pageIndex % columns;
            int row = pageIndex / columns;
            int x = cardStartX + col * (cardWidth + gap);
            int y = cardStartY + row * (cardHeight + gap);
            if (y + cardHeight > panelBottom - 8) {
                break;
            }
            guiGraphics.fill(x, y, x + cardWidth, y + cardHeight, 0x66323A42);
            renderScaledItem(guiGraphics, offer.item(), x + (cardWidth - 24) / 2, y + 10, 1.5F);
            drawCenteredString(guiGraphics, trim(offer.item().getHoverName().getString(), cardWidth - 8), x, y + 42, cardWidth, 0xF3F0E8);
            drawCenteredString(guiGraphics, offer.unitPrice() + " " + MarketEconomy.CURRENCY_NAME, x, y + 57, cardWidth, 0xD6E8D2);
            systemTradeButtons.get(i).setRectangle(tradeButtonWidth, tradeButtonHeight,
                    x + (cardWidth - tradeButtonWidth) / 2, y + cardHeight - tradeButtonBottomPadding);
            systemTradeButtons.get(i).visible = true;
            systemTradeButtons.get(i).active = true;
            visibleIndex++;
        }
        if (visibleIndex == 0) {
            guiGraphics.drawString(font, "No offers in " + selectedSystemBuyCategory + " yet.", cardStartX, panelY + 70, 0xAEB7C4, false);
        }
        renderScrollbar(guiGraphics, panelRight - 8, cardStartY, panelBottom - 8, systemBuyScrollRow, totalRows, rows);
    }

    private void renderSystemSellPanel(GuiGraphics guiGraphics, int panelX, int panelY, int panelRight, int panelBottom,
            int cardWidth, int cardHeight, int gap, int tradeButtonWidth, int tradeButtonHeight, int tradeButtonBottomPadding) {
        int cardStartX = panelX + 14;
        int cardStartY = panelY + 10;
        int columns = Math.max(1, (panelRight - cardStartX - 10 + gap) / (cardWidth + gap));
        int rows = Math.max(1, (panelBottom - cardStartY - 8 + gap) / (cardHeight + gap));
        int visibleOfferCount = systemVisibleOfferCount();
        int totalRows = Math.max(1, (visibleOfferCount + columns - 1) / columns);
        systemSellScrollRow = clampScrollRow(systemSellScrollRow, totalRows, rows);
        int firstVisibleOffer = systemSellScrollRow * columns;
        int lastVisibleOffer = firstVisibleOffer + rows * columns;
        List<SystemMarketOffer> systemOffers = SystemMarketOffer.offers();
        int visibleIndex = 0;
        for (int i = 0; i < systemOffers.size(); i++) {
            SystemMarketOffer offer = systemOffers.get(i);
            if (!isSystemOfferVisible(offer)) {
                continue;
            }
            if (visibleIndex < firstVisibleOffer) {
                visibleIndex++;
                continue;
            }
            if (visibleIndex >= lastVisibleOffer) {
                break;
            }
            int pageIndex = visibleIndex - firstVisibleOffer;
            int col = pageIndex % columns;
            int row = pageIndex / columns;
            int x = cardStartX + col * (cardWidth + gap);
            int y = cardStartY + row * (cardHeight + gap);
            if (y + cardHeight > panelBottom - 8) {
                break;
            }
            guiGraphics.fill(x, y, x + cardWidth, y + cardHeight, 0x66323A42);
            renderScaledItem(guiGraphics, offer.item(), x + (cardWidth - 24) / 2, y + 10, 1.5F);
            drawCenteredString(guiGraphics, trim(offer.item().getHoverName().getString(), cardWidth - 8), x, y + 42, cardWidth, 0xF3F0E8);
            drawCenteredString(guiGraphics, offer.unitPrice() + " " + MarketEconomy.CURRENCY_NAME, x, y + 57, cardWidth, 0xD6E8D2);
            systemTradeButtons.get(i).setRectangle(tradeButtonWidth, tradeButtonHeight,
                    x + (cardWidth - tradeButtonWidth) / 2, y + cardHeight - tradeButtonBottomPadding);
            systemTradeButtons.get(i).visible = true;
            systemTradeButtons.get(i).active = true;
            visibleIndex++;
        }
        if (visibleIndex == 0) {
            guiGraphics.drawString(font, "No system buyback offers yet.", panelX + 18, panelY + 70, 0xAEB7C4, false);
        }
        renderScrollbar(guiGraphics, panelRight - 8, cardStartY, panelBottom - 8, systemSellScrollRow, totalRows, rows);
    }

    private void renderAdminPanel(GuiGraphics guiGraphics) {
        int panelX = leftPos + 18;
        int panelY = topPos + 30;
        int panelRight = leftPos + imageWidth - 18;
        int panelBottom = topPos + imageHeight - 42;
        int inputX = panelX + 16;
        int inputY = panelY + 34;
        int labelColor = 0xD9DEE8;

        int categoryNameBoxX = inputX;
        int categoryNameBoxY = inputY + 40;
        int searchBoxX = inputX + 306;
        int searchBoxY = inputY + 40;
        int searchBoxWidth = 130;
        int searchBoxHeight = 18;
        int categorySlotButtonX = inputX;
        int categorySlotButtonY = inputY + 6;
        int categorySlotButtonWidth = 45;
        int categorySlotButtonHeight = 16;
        int categorySlotButtonGap = 54;
        int gridX = adminGridX();
        int gridY = adminGridY();
        int cellSize = 24;
        int gridColumns = Math.max(1, (panelRight - gridX - 18) / cellSize);
        int gridRows = Math.max(1, (panelBottom - gridY - 8) / cellSize);
        int itemEditorX = leftPos + 115;
        int itemEditorY = topPos + 82;
        int itemEditorPriceX = itemEditorX + 214;
        int itemEditorPriceY = itemEditorY + 45;
        int itemEditorButtonX = itemEditorX + 18;
        int itemEditorButtonY = itemEditorY + 96;
        int itemEditorButtonWidth = 62;
        int itemEditorButtonHeight = 18;
        int itemEditorButtonGap = 8;
        boolean editingItem = selectedAdminItem != null;

        if (adminIdBox != null) {
            adminIdBox.setRectangle(1, 1, leftPos - 1000, topPos - 1000);
        }
        if (adminItemBox != null) {
            adminItemBox.setRectangle(searchBoxWidth, searchBoxHeight, searchBoxX, searchBoxY);
        }
        if (adminPriceBox != null) {
            adminPriceBox.setRectangle(80, 18, selectedAdminItem == null ? leftPos - 1000 : itemEditorPriceX, itemEditorPriceY);
        }
        if (adminOfferCategoryBox != null) {
            adminOfferCategoryBox.setRectangle(1, 1, leftPos - 1000, topPos - 1000);
            adminOfferCategoryBox.setValue(selectedAdminCategorySlotName());
        }
        if (adminCategoryNameBox != null) {
            adminCategoryNameBox.setRectangle(100, 18, categoryNameBoxX, categoryNameBoxY);
        }
        if (adminAddBuyButton != null) {
            adminAddBuyButton.setRectangle(itemEditorButtonWidth, itemEditorButtonHeight, itemEditorButtonX, itemEditorButtonY);
        }
        if (adminAddSellButton != null) {
            adminAddSellButton.setRectangle(itemEditorButtonWidth, itemEditorButtonHeight, itemEditorButtonX + (itemEditorButtonWidth + itemEditorButtonGap), itemEditorButtonY);
        }
        if (adminRemoveButton != null) {
            adminRemoveButton.setRectangle(itemEditorButtonWidth, itemEditorButtonHeight, itemEditorButtonX + (itemEditorButtonWidth + itemEditorButtonGap) * 2, itemEditorButtonY);
        }
        if (adminRemoveSellButton != null) {
            adminRemoveSellButton.setRectangle(itemEditorButtonWidth, itemEditorButtonHeight, itemEditorButtonX + (itemEditorButtonWidth + itemEditorButtonGap) * 3, itemEditorButtonY);
        }
        if (adminReloadButton != null) {
            adminReloadButton.setRectangle(100, 18, panelRight - 112, panelY + 8);
        }
        if (adminAddCategoryButton != null) {
            adminAddCategoryButton.setRectangle(92, 18, categoryNameBoxX + 112, categoryNameBoxY);
        }
        if (adminRemoveCategoryButton != null) {
            adminRemoveCategoryButton.setRectangle(76, 18, categoryNameBoxX + 212, categoryNameBoxY);
        }
        for (int i = 0; i < adminCategorySlotButtons.size(); i++) {
            adminCategorySlotButtons.get(i).setRectangle(categorySlotButtonWidth, categorySlotButtonHeight,
                    categorySlotButtonX + i * categorySlotButtonGap, categorySlotButtonY);
        }

        guiGraphics.fill(panelX, panelY, panelRight, panelBottom, 0x5522272E);
        if (!editingItem) {
            guiGraphics.drawString(font, "System Market Admin", panelX + 12, panelY + 10, 0xF4E7C5, false);
            if (!adminStatus.isBlank()) {
                guiGraphics.drawString(font, trim(adminStatus, panelRight - panelX - 24), panelX + 150, panelY + 10, 0xD6E8D2, false);
            }
            guiGraphics.drawString(font, "Category Slot", categorySlotButtonX, categorySlotButtonY - 10, labelColor, false);
            guiGraphics.drawString(font, "New Category Name", categoryNameBoxX, categoryNameBoxY - 10, labelColor, false);
            guiGraphics.drawString(font, "Selected: " + selectedAdminCategorySlotName(), categoryNameBoxX + 306, categoryNameBoxY + 5, 0xAEB7C4, false);
            guiGraphics.drawString(font, "Search", searchBoxX, searchBoxY - 10, labelColor, false);
            guiGraphics.drawString(font, "Click an item below to configure it.", gridX, gridY - 12, 0xAEB7C4, false);

            List<Item> adminItems = adminItems();
            adminItemScrollRow = clampScrollRow(adminItemScrollRow, Math.max(1, (adminItems.size() + gridColumns - 1) / gridColumns), gridRows);
            int startIndex = adminItemScrollRow * gridColumns;
            int endIndex = Math.min(adminItems.size(), startIndex + gridColumns * gridRows);
            for (int i = startIndex; i < endIndex; i++) {
                int visibleIndex = i - startIndex;
                int col = visibleIndex % gridColumns;
                int row = visibleIndex / gridColumns;
                int x = gridX + col * cellSize;
                int y = gridY + row * cellSize;
                ItemStack stack = new ItemStack(adminItems.get(i));
                guiGraphics.fill(x, y, x + 22, y + 22, 0x55323A42);
                guiGraphics.renderItem(stack, x + 3, y + 3);
            }
            renderScrollbar(guiGraphics, panelRight - 8, gridY, panelBottom - 8, adminItemScrollRow,
                    Math.max(1, (adminItems.size() + gridColumns - 1) / gridColumns), gridRows);
        }

        if (editingItem) {
            renderAdminItemEditor(guiGraphics);
        }
    }

    private void renderAdminItemEditor(GuiGraphics guiGraphics) {
        int x = leftPos + 118;
        int y = topPos + 82;
        int w = 300;
        int h = 124;
        int iconX = x + 20;
        int iconY = y + 26;
        int nameX = x + 56;
        int nameY = y + 20;
        int idY = y + 36;
        int slotY = y + 52;
        int priceLabelX = x + 176;
        int priceLabelY = y + 50;
        int hintX = x + 18;
        int hintY = y + 76;
        int overlayX = leftPos + 8;
        int overlayY = topPos + 24;
        int overlayRight = leftPos + imageWidth - 8;
        int overlayBottom = topPos + 266;
        ItemStack stack = new ItemStack(selectedAdminItem);
        guiGraphics.fill(overlayX, overlayY, overlayRight, overlayBottom, 0xF0000000);
        guiGraphics.fill(x, y, x + w, y + h, 0xF022252B);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xF02F343C);
        guiGraphics.renderItem(stack, iconX, iconY);
        guiGraphics.drawString(font, trim(stack.getHoverName().getString(), 210), nameX, nameY, 0xF3F0E8, false);
        guiGraphics.drawString(font, trim(BuiltInRegistries.ITEM.getKey(selectedAdminItem).toString(), 210), nameX, idY, 0xAEB7C4, false);
        guiGraphics.drawString(font, "Slot: " + selectedAdminCategorySlotName(), nameX, slotY, 0xD6E8D2, false);
        guiGraphics.drawString(font, "Price", priceLabelX, priceLabelY, 0xD9DEE8, false);
        guiGraphics.drawString(font, "Buy uses selected slot. Sell creates buyback.", hintX, hintY, 0xAEB7C4, false);
    }

    private void renderBuyConfirmation(GuiGraphics guiGraphics) {
        clampBuyCountBox();
        ItemStack item = confirmationItem();
        int available = confirmationAvailable();
        long unitPrice = confirmationUnitPrice();
        int x = leftPos + 125;
        int y = topPos + 70;
        int boxWidth = 251;
        int boxHeight = 151;
        int countBoxX = leftPos + 275;
        int countBoxY = topPos + 154;
        int countBoxWidth = 50;
        int countBoxHeight = 15;
        int confirmX = leftPos + 160;
        int cancelX = leftPos + 260;
        int buttonY = topPos + 195;
        int buttonWidth = 82;
        int buttonHeight = 18;

        if (buyCountBox != null) {
            buyCountBox.setRectangle(countBoxWidth, countBoxHeight, countBoxX, countBoxY);
        }
        if (confirmBuyButton != null) {
            confirmBuyButton.setRectangle(buttonWidth, buttonHeight, confirmX, buttonY);
        }
        if (cancelBuyButton != null) {
            cancelBuyButton.setRectangle(buttonWidth, buttonHeight, cancelX, buttonY);
        }

        guiGraphics.fill(leftPos + 8, topPos + 24, leftPos + imageWidth - 8, topPos + 266, 0xAA000000);
        guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, 0xF022252B);
        guiGraphics.fill(x + 1, y + 1, x + boxWidth - 1, y + boxHeight - 1, 0xF02F343C);

        guiGraphics.drawString(font, confirmationTitle(), x + 12, y + 10, 0xF4E7C5, false);
        renderScaledItem(guiGraphics, item, x + 60, y + 25, 2.0F);
        guiGraphics.drawString(font, trim(item.getHoverName().getString(), 190), x + 120, y + 31, 0xF3F0E8, false);
        guiGraphics.drawString(font, confirmationAvailableLabel(available), x + 120, y + 45, 0xAEB7C4, false);
        guiGraphics.drawString(font, "Unit price", x + 50, y + 70, 0xD9DEE8, false);
        guiGraphics.drawString(font, unitPrice + " " + MarketEconomy.CURRENCY_NAME, x + 160, y + 70, 0xD6E8D2, false);
        guiGraphics.drawString(font, "Count", x + 50, y + 88, 0xD9DEE8, false);
        guiGraphics.drawString(font, "Total", x + 50, y + 106, 0xD9DEE8, false);
        guiGraphics.drawString(font, buyTotalPrice() + " " + MarketEconomy.CURRENCY_NAME, x + 160, y + 106, 0xD6E8D2, false);
    }

    private void renderItemTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (sellMode || systemMode || isConfirming()) {
            return;
        }
        List<MarketMenuEntry> entries = menu.snapshot().entries();
        int panelTop = topPos + 34;
        int rowHeight = 42;
        int visibleRows = Math.max(1, (topPos + 266 - panelTop) / rowHeight);
        int startEntry = marketScrollRow * 2;
        int endEntry = Math.min(entries.size(), startEntry + visibleRows * 2);
        for (int i = startEntry; i < endEntry; i++) {
            int visibleIndex = i - startEntry;
            int col = visibleIndex % 2;
            int row = visibleIndex / 2;
            int x = leftPos + 22 + col * 244;
            int y = topPos + 42 + row * 42;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                ItemStack stack = entries.get(i).item();
                guiGraphics.renderTooltip(font, Screen.getTooltipFromItem(minecraft, stack), stack.getTooltipImage(), stack, mouseX, mouseY);
                return;
            }
        }
    }

    private void updateButtonStates() {
        MarketMenuSnapshot snapshot = menu.snapshot();
        boolean confirmingBuy = isConfirming();
        List<String> categories = SystemMarketOffer.buyCategories();
        if (!categories.isEmpty() && !categories.contains(selectedSystemBuyCategory)) {
            selectedSystemBuyCategory = categories.getFirst();
            systemBuyScrollRow = 0;
        }
        for (int i = 0; i < buyButtons.size(); i++) {
            Button button = buyButtons.get(i);
            button.visible = false;
            button.active = false;
        }
        if (previousButton != null) {
            previousButton.visible = false;
            previousButton.active = false;
        }
        if (nextButton != null) {
            nextButton.visible = false;
            nextButton.active = false;
        }
        if (refreshButton != null) {
            refreshButton.visible = !sellMode && !systemMode && !adminMode && !confirmingBuy;
            refreshButton.active = !sellMode && !systemMode && !adminMode && !confirmingBuy;
        }
        if (priceBox != null) {
            priceBox.visible = sellMode;
            priceBox.active = sellMode && !confirmingBuy;
        }
        if (countBox != null) {
            countBox.visible = sellMode;
            countBox.active = sellMode && !confirmingBuy;
        }
        if (buyCountBox != null) {
            buyCountBox.visible = confirmingBuy;
            buyCountBox.active = confirmingBuy;
        }
        if (submitSellButton != null) {
            submitSellButton.visible = sellMode;
            submitSellButton.active = sellMode && !confirmingBuy && parsePrice() > 0L && parseCount() > 0 && maxSellCount() > 0;
        }
        if (confirmBuyButton != null) {
            confirmBuyButton.visible = confirmingBuy;
            confirmBuyButton.active = confirmingBuy && parseBuyCount() > 0 && confirmationAvailable() > 0;
        }
        if (cancelBuyButton != null) {
            cancelBuyButton.visible = confirmingBuy;
            cancelBuyButton.active = confirmingBuy;
        }
        if (listTabButton != null) {
            listTabButton.active = (sellMode || systemMode || adminMode) && !confirmingBuy;
        }
        if (sellTabButton != null) {
            sellTabButton.active = !sellMode && !confirmingBuy;
        }
        if (systemTabButton != null) {
            systemTabButton.active = !systemMode && !confirmingBuy;
        }
        if (adminTabButton != null) {
            adminTabButton.active = !adminMode && !confirmingBuy;
        }
        if (systemBuyTabButton != null) {
            systemBuyTabButton.visible = systemMode && !confirmingBuy;
            systemBuyTabButton.active = systemMode && systemSellPage && !confirmingBuy;
        }
        if (systemSellTabButton != null) {
            systemSellTabButton.visible = systemMode && !confirmingBuy;
            systemSellTabButton.active = systemMode && !systemSellPage && !confirmingBuy;
        }
        for (int i = 0; i < systemCategoryButtons.size(); i++) {
            Button button = systemCategoryButtons.get(i);
            if (i >= categories.size()) {
                button.visible = false;
                button.active = false;
                continue;
            }
            String category = categories.get(i);
            button.setMessage(Component.literal(category));
            button.visible = systemMode && !systemSellPage && !confirmingBuy;
            button.active = button.visible && !category.equals(selectedSystemBuyCategory);
        }
        List<SystemMarketOffer> systemOffers = SystemMarketOffer.offers();
        for (int i = 0; i < systemTradeButtons.size(); i++) {
            if (i >= systemOffers.size()) {
                systemTradeButtons.get(i).visible = false;
                systemTradeButtons.get(i).active = false;
                continue;
            }
            SystemMarketOffer offer = systemOffers.get(i);
            Button button = systemTradeButtons.get(i);
            boolean visible = systemMode && !confirmingBuy && isSystemOfferVisible(offer);
            button.setMessage(Component.literal(systemButtonText(offer)));
            button.visible = visible;
            button.active = visible;
        }
        updateAdminWidgetStates(confirmingBuy);
    }

    private void updateAdminWidgetStates(boolean confirmingBuy) {
        boolean active = adminMode && !confirmingBuy;
        if (adminIdBox != null) {
            adminIdBox.visible = false;
            adminIdBox.active = false;
        }
        if (adminItemBox != null) {
            adminItemBox.visible = active && selectedAdminItem == null;
            adminItemBox.active = active && selectedAdminItem == null;
            if (selectedAdminItem != null) {
                adminItemBox.setFocused(false);
            }
        }
        if (adminPriceBox != null) {
            adminPriceBox.visible = active && selectedAdminItem != null;
            adminPriceBox.active = active && selectedAdminItem != null;
        }
        if (adminOfferCategoryBox != null) {
            adminOfferCategoryBox.visible = active;
            adminOfferCategoryBox.active = false;
        }
        if (adminCategoryNameBox != null) {
            adminCategoryNameBox.visible = active && selectedAdminItem == null;
            adminCategoryNameBox.active = active && selectedAdminItem == null;
            if (selectedAdminItem != null) {
                adminCategoryNameBox.setFocused(false);
            }
        }
        if (adminAddBuyButton != null) {
            adminAddBuyButton.visible = active && selectedAdminItem != null;
            adminAddBuyButton.active = active && selectedAdminItem != null && parseAdminPrice() > 0L;
        }
        if (adminAddSellButton != null) {
            adminAddSellButton.visible = active && selectedAdminItem != null;
            adminAddSellButton.active = active && selectedAdminItem != null && parseAdminPrice() > 0L;
        }
        if (adminRemoveButton != null) {
            adminRemoveButton.visible = active && selectedAdminItem != null;
            adminRemoveButton.active = active && selectedAdminItem != null;
        }
        if (adminRemoveSellButton != null) {
            adminRemoveSellButton.visible = active && selectedAdminItem != null;
            adminRemoveSellButton.active = active && selectedAdminItem != null;
        }
        if (adminReloadButton != null) {
            adminReloadButton.visible = active;
            adminReloadButton.active = active;
        }
        if (adminAddCategoryButton != null) {
            adminAddCategoryButton.visible = active && selectedAdminItem == null;
            adminAddCategoryButton.active = active && selectedAdminItem == null && !adminCategoryNameBox.getValue().isBlank();
        }
        if (adminRemoveCategoryButton != null) {
            adminRemoveCategoryButton.visible = active && selectedAdminItem == null;
            adminRemoveCategoryButton.active = active && selectedAdminItem == null;
        }
        for (int i = 0; i < adminCategorySlotButtons.size(); i++) {
            Button button = adminCategorySlotButtons.get(i);
            button.visible = active && selectedAdminItem == null;
            button.active = active && selectedAdminItem == null && i != selectedAdminCategorySlot;
            button.setMessage(Component.literal("#" + (i + 1)));
        }
    }

    private void buy(int row) {
        List<MarketMenuEntry> entries = menu.snapshot().entries();
        if (row >= 0 && row < visibleMarketEntryIndexes.size()) {
            int entryIndex = visibleMarketEntryIndexes.get(row);
            if (entryIndex >= 0 && entryIndex < entries.size()) {
                openBuyConfirmation(entries.get(entryIndex));
            }
        }
    }

    private void changePage(int delta) {
        scrollActiveView(-delta);
    }

    private void refresh() {
        PacketDistributor.sendToServer(MarketActionPayload.refresh(menu.snapshot().page()));
    }

    private void setSellMode(boolean sellMode) {
        this.sellMode = sellMode;
        this.systemMode = false;
        this.adminMode = false;
        closeConfirmation();
        if (priceBox != null && !sellMode) {
            priceBox.setFocused(false);
        }
        if (countBox != null) {
            if (sellMode && countBox.getValue().isBlank() && maxSellCount() > 0) {
                countBox.setValue("1");
            } else if (!sellMode) {
                countBox.setFocused(false);
            }
        }
        updateButtonStates();
    }

    private void setSystemMode(boolean systemMode) {
        this.systemMode = systemMode;
        this.sellMode = false;
        this.adminMode = false;
        closeConfirmation();
        updateButtonStates();
    }

    private void setAdminMode(boolean adminMode) {
        this.adminMode = adminMode;
        this.sellMode = false;
        this.systemMode = false;
        if (adminMode) {
            rebuildAdminItemCacheIfNeeded();
            refreshAdminFilteredItems();
        }
        closeConfirmation();
        updateButtonStates();
    }

    private void setSystemSellPage(boolean systemSellPage) {
        this.systemSellPage = systemSellPage;
        closeConfirmation();
        updateButtonStates();
    }

    private void setSystemBuyCategory(String category) {
        this.selectedSystemBuyCategory = category;
        this.systemBuyScrollRow = 0;
        closeConfirmation();
        updateButtonStates();
    }

    private void selectSystemBuyCategory(int categoryIndex) {
        List<String> categories = SystemMarketOffer.buyCategories();
        if (categoryIndex >= 0 && categoryIndex < categories.size()) {
            setSystemBuyCategory(categories.get(categoryIndex));
        }
    }

    private void selectAdminCategorySlot(int slot) {
        selectedAdminCategorySlot = Math.max(0, Math.min(slot, 7));
        if (adminOfferCategoryBox != null) {
            adminOfferCategoryBox.setValue(selectedAdminCategorySlotName());
        }
        if (adminCategoryNameBox != null) {
            adminCategoryNameBox.setFocused(true);
        }
        updateButtonStates();
    }

    private void submitSell() {
        long price = parsePrice();
        int count = parseCount();
        if (price > 0L && count > 0) {
            PacketDistributor.sendToServer(MarketActionPayload.sell(price, count, 1));
            setSellMode(false);
            priceBox.setValue("");
            countBox.setValue("");
        }
    }

    private long parsePrice() {
        if (priceBox == null || priceBox.getValue().isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(priceBox.getValue());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private int parseCount() {
        if (countBox == null || countBox.getValue().isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(countBox.getValue());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int parseBuyCount() {
        if (buyCountBox == null || buyCountBox.getValue().isBlank()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(buyCountBox.getValue()));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void openBuyConfirmation(MarketMenuEntry entry) {
        selectedBuyEntry = entry;
        selectedSystemOffer = null;
        setBuyCount(1);
        if (buyCountBox != null) {
            buyCountBox.setFocused(true);
        }
        updateButtonStates();
    }

    private void closeConfirmation() {
        selectedBuyEntry = null;
        selectedSystemOffer = null;
        if (buyCountBox != null) {
            buyCountBox.setFocused(false);
            setBuyCount(1);
        }
        updateButtonStates();
    }

    private void confirmTrade() {
        if (!isConfirming()) {
            return;
        }
        int available = confirmationAvailable();
        if (available <= 0) {
            closeConfirmation();
            return;
        }
        int count = Math.max(1, Math.min(parseBuyCount(), available));
        if (selectedBuyEntry != null) {
            PacketDistributor.sendToServer(MarketActionPayload.buy(selectedBuyEntry.id(), count, 1));
        } else {
            PacketDistributor.sendToServer(MarketActionPayload.systemTrade(selectedSystemOffer.id(), count, 1));
        }
        closeConfirmation();
    }

    private void openSystemConfirmation(SystemMarketOffer offer) {
        selectedBuyEntry = null;
        selectedSystemOffer = offer;
        setBuyCount(1);
        if (buyCountBox != null) {
            buyCountBox.setFocused(true);
        }
        updateButtonStates();
    }

    private void openSystemOffer(int offerIndex) {
        List<SystemMarketOffer> offers = SystemMarketOffer.offers();
        if (offerIndex >= 0 && offerIndex < offers.size()) {
            openSystemConfirmation(offers.get(offerIndex));
        }
    }

    private String systemButtonText(SystemMarketOffer offer) {
        return offer.type() == SystemMarketOffer.Type.SYSTEM_SELLS ? "Buy" : "Sell";
    }

    private void submitAdminOffer(String offerType) {
        long price = parseAdminPrice();
        if (selectedAdminItem == null || price <= 0L) {
            return;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(selectedAdminItem).toString();
        String category = "";
        if ("sell_to_player".equals(offerType)) {
            category = selectedAdminCategorySlotName();
        }
        PacketDistributor.sendToServer(MarketAdminActionPayload.add(
                generatedAdminOfferId(offerType, itemId, category),
                offerType,
                itemId,
                price,
                category));
        adminStatus = "sell_to_player".equals(offerType) ? "Sent add buy offer request." : "Sent add sell offer request.";
    }

    private void removeAdminOffer() {
        if (selectedAdminItem != null) {
            String itemId = BuiltInRegistries.ITEM.getKey(selectedAdminItem).toString();
            PacketDistributor.sendToServer(MarketAdminActionPayload.remove(generatedAdminOfferId("sell_to_player", itemId, selectedAdminCategorySlotName())));
            adminStatus = "Sent remove buy offer request.";
        }
    }

    private void removeAdminSellOffer() {
        if (selectedAdminItem != null) {
            String itemId = BuiltInRegistries.ITEM.getKey(selectedAdminItem).toString();
            PacketDistributor.sendToServer(MarketAdminActionPayload.remove(generatedAdminOfferId("buy_from_player", itemId, "")));
            adminStatus = "Sent remove sell offer request.";
        }
    }

    private void reloadAdminConfig() {
        PacketDistributor.sendToServer(MarketAdminActionPayload.reload());
        adminStatus = "Sent reload request.";
    }

    private void renameAdminCategory() {
        if (adminCategoryNameBox != null && !adminCategoryNameBox.getValue().isBlank()) {
            PacketDistributor.sendToServer(MarketAdminActionPayload.renameCategory(selectedAdminCategorySlotName(), adminCategoryNameBox.getValue()));
            adminStatus = "Sent rename category request.";
        }
    }

    private void resetAdminCategory() {
        PacketDistributor.sendToServer(MarketAdminActionPayload.resetCategory(selectedAdminCategorySlotName()));
        adminStatus = "Sent reset category request.";
    }

    private String selectedAdminCategorySlotName() {
        List<String> categories = SystemMarketOffer.buyCategories();
        if (selectedAdminCategorySlot >= 0 && selectedAdminCategorySlot < categories.size()) {
            return categories.get(selectedAdminCategorySlot);
        }
        return "#" + (selectedAdminCategorySlot + 1);
    }

    private long parseAdminPrice() {
        if (adminPriceBox == null || adminPriceBox.getValue().isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(adminPriceBox.getValue());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String generatedAdminOfferId(String offerType, String itemId, String category) {
        String prefix = "sell_to_player".equals(offerType) ? "buy" : "sell";
        String slot = "sell_to_player".equals(offerType) ? "cat" + (selectedAdminCategorySlot + 1) + "_" : "";
        String raw = prefix + "_" + slot + itemId;
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                id.append(Character.toLowerCase(c));
            } else {
                id.append('_');
            }
        }
        return id.toString().replaceAll("_+", "_").replaceAll("^_|_$", "");
    }

    private boolean selectAdminItemAt(double mouseX, double mouseY) {
        int panelRight = leftPos + imageWidth - 18;
        int gridX = adminGridX();
        int gridY = adminGridY();
        int cellSize = 24;
        int columns = Math.max(1, (panelRight - gridX - 18) / cellSize);
        int rows = Math.max(1, (topPos + imageHeight - 50 - gridY) / cellSize);
        if (mouseX < gridX || mouseY < gridY || mouseX >= gridX + columns * cellSize || mouseY >= gridY + rows * cellSize) {
            return false;
        }
        int col = (int)((mouseX - gridX) / cellSize);
        int row = (int)((mouseY - gridY) / cellSize);
        int index = adminItemScrollRow * columns + row * columns + col;
        List<Item> items = adminItems();
        if (index < 0 || index >= items.size()) {
            return false;
        }
        selectedAdminItem = items.get(index);
        adminSellOfferMode = false;
        if (adminCategoryNameBox != null) {
            adminCategoryNameBox.setFocused(false);
        }
        if (adminPriceBox != null) {
            adminPriceBox.setValue(existingAdminPriceText(selectedAdminItem, false));
            adminPriceBox.setFocused(true);
        }
        updateButtonStates();
        return true;
    }

    private boolean isInsideAdminItemEditor(double mouseX, double mouseY) {
        int x = leftPos + 118;
        int y = topPos + 92;
        int w = 260;
        int h = 94;
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private void closeAdminItemEditor() {
        selectedAdminItem = null;
        if (adminPriceBox != null) {
            adminPriceBox.setFocused(false);
            adminPriceBox.setValue("");
        }
        updateButtonStates();
    }

    private String existingAdminPriceText(Item item, boolean sellOffer) {
        String id = generatedAdminOfferId(sellOffer ? "buy_from_player" : "sell_to_player",
                BuiltInRegistries.ITEM.getKey(item).toString(),
                sellOffer ? "" : selectedAdminCategorySlotName());
        for (SystemMarketOffer offer : SystemMarketOffer.offers()) {
            if (offer.id().equals(id)) {
                return Long.toString(offer.unitPrice());
            }
        }
        return "";
    }

    private List<Item> adminItems() {
        rebuildAdminItemCacheIfNeeded();
        String search = adminItemBox == null ? "" : adminItemBox.getValue().trim();
        if (!search.equals(adminLastSearch)) {
            refreshAdminFilteredItems();
        }
        return adminFilteredItems;
    }

    private void rebuildAdminItemCacheIfNeeded() {
        if (adminAllItems != null) {
            return;
        }
        adminAllItems = BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                .toList();
        adminFilteredItems = adminAllItems;
    }

    private void refreshAdminFilteredItems() {
        rebuildAdminItemCacheIfNeeded();
        String search = adminItemBox == null ? "" : adminItemBox.getValue().trim().toLowerCase();
        adminLastSearch = search;
        adminItemScrollRow = 0;
        selectedAdminItem = null;
        if (search.isEmpty()) {
            adminFilteredItems = adminAllItems;
            return;
        }
        adminFilteredItems = adminAllItems.stream()
                .filter(item -> matchesAdminSearch(item, search))
                .toList();
    }

    private boolean matchesAdminSearch(Item item, String search) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id.toString().toLowerCase().contains(search)) {
            return true;
        }
        return new ItemStack(item).getHoverName().getString().toLowerCase().contains(search);
    }

    private static String firstSystemCategory() {
        List<String> categories = SystemMarketOffer.buyCategories();
        return categories.isEmpty() ? "#1" : categories.getFirst();
    }

    private int maxSellCount() {
        if (minecraft.player == null) {
            return 0;
        }
        ItemStack held = minecraft.player.getInventory().getSelected();
        return InventoryUtil.countMatching(minecraft.player.getInventory(), held);
    }

    private void clampCountBox() {
        if (updatingCountBox || countBox == null || !sellMode || countBox.getValue().isBlank()) {
            return;
        }

        int max = maxSellCount();
        int count = parseCount();
        int clamped = Math.max(0, Math.min(count, max));
        if (count != clamped) {
            updatingCountBox = true;
            countBox.setValue(clamped > 0 ? Integer.toString(clamped) : "");
            updatingCountBox = false;
        }
    }

    private void clampBuyCountBox() {
        if (updatingBuyCountBox || buyCountBox == null || !isConfirming() || buyCountBox.getValue().isBlank()) {
            return;
        }

        int count = parseBuyCount();
        int clamped = Math.max(1, Math.min(count, confirmationAvailable()));
        if (count != clamped) {
            setBuyCount(clamped);
        }
    }

    private void setBuyCount(int count) {
        if (buyCountBox == null) {
            return;
        }
        updatingBuyCountBox = true;
        buyCountBox.setValue(Integer.toString(Math.max(1, count)));
        updatingBuyCountBox = false;
    }

    private long buyTotalPrice() {
        if (!isConfirming()) {
            return 0L;
        }
        try {
            return Math.multiplyExact(confirmationUnitPrice(), parseBuyCount());
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private boolean isConfirming() {
        return selectedBuyEntry != null || selectedSystemOffer != null;
    }

    private ItemStack confirmationItem() {
        return selectedBuyEntry != null ? selectedBuyEntry.item() : selectedSystemOffer.item();
    }

    private long confirmationUnitPrice() {
        return selectedBuyEntry != null ? selectedBuyEntry.price() : selectedSystemOffer.unitPrice();
    }

    private int confirmationAvailable() {
        if (selectedBuyEntry != null) {
            return selectedBuyEntry.count();
        }
        if (selectedSystemOffer.type() == SystemMarketOffer.Type.SYSTEM_SELLS) {
            return 999;
        }
        return minecraft == null || minecraft.player == null ? 0 : InventoryUtil.countMatching(minecraft.player.getInventory(), selectedSystemOffer.item());
    }

    private String confirmationTitle() {
        if (selectedBuyEntry != null || selectedSystemOffer.type() == SystemMarketOffer.Type.SYSTEM_SELLS) {
            return "Confirm Purchase";
        }
        return "Confirm Sale";
    }

    private String confirmationAvailableLabel(int available) {
        if (selectedSystemOffer != null && selectedSystemOffer.type() == SystemMarketOffer.Type.SYSTEM_SELLS) {
            return "Available: unlimited";
        }
        return "Available: " + available;
    }

    private boolean isSystemOfferVisible(SystemMarketOffer offer) {
        if (systemSellPage) {
            return offer.type() == SystemMarketOffer.Type.SYSTEM_BUYS;
        }
        return offer.type() == SystemMarketOffer.Type.SYSTEM_SELLS && selectedSystemBuyCategory.equals(offer.buyCategory());
    }

    private int systemVisibleOfferCount() {
        int count = 0;
        for (SystemMarketOffer offer : SystemMarketOffer.offers()) {
            if (isSystemOfferVisible(offer)) {
                count++;
            }
        }
        return count;
    }

    private void scrollActiveView(int deltaRows) {
        if (deltaRows == 0) {
            return;
        }
        if (adminMode) {
            adminItemScrollRow = Math.max(0, adminItemScrollRow + deltaRows);
        } else if (systemMode) {
            if (systemSellPage) {
                systemSellScrollRow = Math.max(0, systemSellScrollRow + deltaRows);
            } else {
                systemBuyScrollRow = Math.max(0, systemBuyScrollRow + deltaRows);
            }
        } else {
            marketScrollRow = Math.max(0, marketScrollRow + deltaRows);
        }
        updateButtonStates();
    }

    private int clampScrollRow(int scrollRow, int totalRows, int visibleRows) {
        return Math.max(0, Math.min(scrollRow, Math.max(0, totalRows - visibleRows)));
    }

    private boolean isInsideContent(double mouseX, double mouseY) {
        return mouseX >= leftPos + 8 && mouseX <= leftPos + imageWidth - 8 && mouseY >= topPos + 24 && mouseY <= topPos + 266;
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int x, int y, int bottom, int scrollRow, int totalRows, int visibleRows) {
        if (totalRows <= visibleRows) {
            return;
        }
        int trackWidth = 4;
        int trackHeight = bottom - y;
        int thumbHeight = Math.max(18, trackHeight * visibleRows / totalRows);
        int maxScroll = Math.max(1, totalRows - visibleRows);
        int thumbTravel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = y + thumbTravel * scrollRow / maxScroll;
        guiGraphics.fill(x, y, x + trackWidth, bottom, 0x77323A42);
        guiGraphics.fill(x, thumbY, x + trackWidth, thumbY + thumbHeight, 0xCC9AA0AA);
    }

    private boolean isInsideActiveScrollbar(double mouseX, double mouseY) {
        int totalRows = activeScrollTotalRows();
        int visibleRows = activeScrollVisibleRows();
        if (totalRows <= visibleRows) {
            return false;
        }
        int x = activeScrollbarX();
        int y = activeScrollbarY();
        int bottom = activeScrollbarBottom();
        return mouseX >= x - 2 && mouseX <= x + 8 && mouseY >= y && mouseY <= bottom;
    }

    private int activeScrollbarTarget() {
        if (adminMode) {
            return 4;
        }
        if (systemMode) {
            return systemSellPage ? 3 : 2;
        }
        return 1;
    }

    private void setActiveScrollFromMouse(double mouseY) {
        int totalRows = activeScrollTotalRows();
        int visibleRows = activeScrollVisibleRows();
        int maxScroll = Math.max(0, totalRows - visibleRows);
        if (maxScroll <= 0) {
            return;
        }
        int y = activeScrollbarY();
        int trackHeight = activeScrollbarBottom() - y;
        int thumbHeight = Math.max(18, trackHeight * visibleRows / totalRows);
        int thumbTravel = Math.max(1, trackHeight - thumbHeight);
        int scrollRow = Math.round((float)((mouseY - y - thumbHeight / 2.0D) * maxScroll / thumbTravel));
        scrollRow = Math.max(0, Math.min(scrollRow, maxScroll));
        if (draggingScrollbar == 4 || (draggingScrollbar == 0 && adminMode)) {
            adminItemScrollRow = scrollRow;
        } else if (draggingScrollbar == 3 || (draggingScrollbar == 0 && systemMode && systemSellPage)) {
            systemSellScrollRow = scrollRow;
        } else if (draggingScrollbar == 2 || (draggingScrollbar == 0 && systemMode)) {
            systemBuyScrollRow = scrollRow;
        } else {
            marketScrollRow = scrollRow;
        }
        updateButtonStates();
    }

    private int activeScrollbarX() {
        if (adminMode) {
            return leftPos + imageWidth - 26;
        }
        if (systemMode) {
            return leftPos + imageWidth - 22;
        }
        return leftPos + imageWidth - 14;
    }

    private int activeScrollbarY() {
        if (adminMode) {
            return adminGridY();
        }
        if (systemMode) {
            return systemSellPage ? topPos + 65 : topPos + 70;
        }
        return topPos + 34;
    }

    private int activeScrollbarBottom() {
        if (adminMode) {
            return topPos + imageHeight - 50;
        }
        return systemMode ? topPos + imageHeight - 42 : topPos + 266;
    }

    private int activeScrollTotalRows() {
        if (adminMode) {
            int columns = activeAdminItemColumns();
            return Math.max(1, (adminItems().size() + columns - 1) / columns);
        }
        if (systemMode) {
            int columns = activeSystemColumns();
            return Math.max(1, (systemVisibleOfferCount() + columns - 1) / columns);
        }
        return Math.max(1, (menu.snapshot().entries().size() + 1) / 2);
    }

    private int activeScrollVisibleRows() {
        if (adminMode) {
            int gridY = adminGridY();
            int cellSize = 24;
            return Math.max(1, (topPos + imageHeight - 50 - gridY) / cellSize);
        }
        if (systemMode) {
            int cardStartY = systemSellPage ? topPos + 65 : topPos + 70;
            int cardHeight = 85;
            int gap = 10;
            int panelBottom = topPos + imageHeight - 34;
            return Math.max(1, (panelBottom - cardStartY - 8 + gap) / (cardHeight + gap));
        }
        return Math.max(1, (topPos + 266 - (topPos + 34)) / 42);
    }

    private int activeSystemColumns() {
        int panelX = leftPos + 14;
        int panelRight = leftPos + imageWidth - 14;
        int categoryColumnWidth = systemSellPage ? 0 : 72;
        int cardStartX = panelX + categoryColumnWidth + (systemSellPage ? 14 : 5);
        int cardWidth = 60;
        int gap = 10;
        return Math.max(1, (panelRight - cardStartX - 10 + gap) / (cardWidth + gap));
    }

    private int activeAdminItemColumns() {
        int panelRight = leftPos + imageWidth - 18;
        int gridX = adminGridX();
        int cellSize = 24;
        return Math.max(1, (panelRight - gridX - 18) / cellSize);
    }

    private int adminGridX() {
        int panelX = leftPos + 18;
        int inputX = panelX + 16;
        return inputX;
    }

    private int adminGridY() {
        int panelY = topPos + 30;
        int inputY = panelY + 34;
        return inputY + 85;
    }

    private String timeLeft(long expiresAt) {
        long millis = expiresAt - System.currentTimeMillis();
        if (millis <= 0L) {
            return "expired";
        }
        Duration duration = Duration.ofMillis(millis);
        long days = duration.toDays();
        if (days > 0L) {
            return days + "d left";
        }
        long hours = duration.toHours();
        if (hours > 0L) {
            return hours + "h left";
        }
        return Math.max(1L, duration.toMinutes()) + "m left";
    }

    private String trim(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        int maxTextWidth = maxWidth - font.width(ellipsis);
        String result = value;
        while (!result.isEmpty() && font.width(result) > maxTextWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + ellipsis;
    }

    private void drawCenteredString(GuiGraphics guiGraphics, String text, int x, int y, int width, int color) {
        guiGraphics.drawString(font, text, x + (width - font.width(text)) / 2, y, color, false);
    }

    private void drawCenteredStringAt(GuiGraphics guiGraphics, String text, int centerX, int y, int color) {
        guiGraphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private void renderScaledItem(GuiGraphics guiGraphics, ItemStack stack, int x, int y, float scale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0F);
        int scaledX = (int)(x / scale);
        int scaledY = (int)(y / scale);
        guiGraphics.renderItem(stack, scaledX, scaledY);
        guiGraphics.pose().popPose();
    }
}
