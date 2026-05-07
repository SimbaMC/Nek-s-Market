package com.nekros.market.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.nekros.market.economy.MarketEconomy;
import com.nekros.market.menu.MarketMenu;
import com.nekros.market.menu.MarketMenuEntry;
import com.nekros.market.menu.MarketMenuSnapshot;
import com.nekros.market.network.MarketActionPayload;
import com.nekros.market.system.SystemMarketOffer;
import com.nekros.market.util.InventoryUtil;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class MarketScreen extends AbstractContainerScreen<MarketMenu> {
    private static final int ROWS = 8;
    private final List<Button> buyButtons = new ArrayList<>();
    private Button previousButton;
    private Button nextButton;
    private Button refreshButton;
    private Button listTabButton;
    private Button sellTabButton;
    private Button systemTabButton;
    private Button systemBuyTabButton;
    private Button systemSellTabButton;
    private Button submitSellButton;
    private Button confirmBuyButton;
    private Button cancelBuyButton;
    private final List<Button> systemTradeButtons = new ArrayList<>();
    private EditBox priceBox;
    private EditBox countBox;
    private EditBox buyCountBox;
    private MarketMenuEntry selectedBuyEntry;
    private SystemMarketOffer selectedSystemOffer;
    private boolean sellMode;
    private boolean systemMode;
    private boolean systemSellPage;
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

        for (int i = 0; i < ROWS; i++) {
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
        systemBuyTabButton = addRenderableWidget(Button.builder(Component.literal("Buy"), ignored -> setSystemSellPage(false))
                .bounds(leftPos + 30, topPos + 36, 64, 18)
                .build());
        systemSellTabButton = addRenderableWidget(Button.builder(Component.literal("Sell"), ignored -> setSystemSellPage(true))
                .bounds(leftPos + 108, topPos + 36, 64, 18)
                .build());

        previousButton = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> changePage(-1))
                .bounds(leftPos + 356, topPos + 274, 28, 16)
                .build());
        refreshButton = addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> refresh())
                .bounds(leftPos + 390, topPos + 274, 58, 16)
                .build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> changePage(1))
                .bounds(leftPos + 454, topPos + 274, 28, 16)
                .build());
        priceBox = addRenderableWidget(new EditBox(font, leftPos + 210, topPos + 90, 96, 18, Component.literal("Price")));
        priceBox.setHint(Component.literal("Price"));
        priceBox.setMaxLength(18);
        priceBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));

        countBox = addRenderableWidget(new EditBox(font, leftPos + 210, topPos + 138, 96, 18, Component.literal("Count")));
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

        submitSellButton = addRenderableWidget(Button.builder(Component.literal("List Item"), ignored -> submitSell())
                .bounds(leftPos + 210, topPos + 158, 96, 18)
                .build());
        confirmBuyButton = addRenderableWidget(Button.builder(Component.literal("Confirm"), ignored -> confirmTrade())
                .bounds(0, 0, 82, 18)
                .build());
        cancelBuyButton = addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> closeConfirmation())
                .bounds(0, 0, 82, 18)
                .build());
        for (int i = 0; i < SystemMarketOffer.OFFERS.size(); i++) {
            final int offerIndex = i;
            Button button = Button.builder(Component.literal(systemButtonText(SystemMarketOffer.OFFERS.get(i))), ignored -> openSystemConfirmation(SystemMarketOffer.OFFERS.get(offerIndex)))
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
        guiGraphics.drawString(font, "Page " + snapshot.page() + "/" + snapshot.pageCount(), leftPos + 214, topPos + 277, 0xD9DEE8, false);

        if (sellMode) {
            renderSellPanel(guiGraphics);
            return;
        }
        if (systemMode) {
            renderSystemPanel(guiGraphics);
            return;
        }

        List<MarketMenuEntry> entries = snapshot.entries();
        if (entries.isEmpty()) {
            guiGraphics.drawString(font, "No listings yet. Use /market sell <price> to list your held item.", leftPos + 18, topPos + 96, 0xB8C0CC, false);
            return;
        }

        for (int i = 0; i < entries.size(); i++) {
            renderEntry(guiGraphics, entries.get(i), i);
        }
        if (selectedBuyEntry != null) {
            renderBuyConfirmation(guiGraphics);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
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
        guiGraphics.drawString(font, "Sell Held Item", leftPos + 18, topPos + 36, 0xF3F0E8, false);
        guiGraphics.drawString(font, "Put the item you want to sell in your main hand.", leftPos + 18, topPos + 52, 0xAEB7C4, false);

        ItemStack held = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getInventory().getSelected();
        guiGraphics.fill(leftPos + 18, topPos + 72, leftPos + 482, topPos + 250, 0x5522272E);
        if (held.isEmpty()) {
            guiGraphics.drawString(font, "Main hand is empty.", leftPos + 36, topPos + 106, 0xD68A8A, false);
        } else {
            guiGraphics.renderItem(held, leftPos + 34, topPos + 96);
            guiGraphics.renderItemDecorations(font, held, leftPos + 34, topPos + 96);
            guiGraphics.drawString(font, trim(held.getHoverName().getString(), 128), leftPos + 58, topPos + 94, 0xF3F0E8, false);
            guiGraphics.drawString(font, "Available: " + maxSellCount(), leftPos + 58, topPos + 108, 0xAEB7C4, false);
        }

        guiGraphics.drawString(font, "Unit price", leftPos + 350, topPos + 90, 0xD9DEE8, false);
        guiGraphics.drawString(font, MarketEconomy.CURRENCY_NAME + " each", leftPos + 350, topPos + 130, 0xAEB7C4, false);
        guiGraphics.drawString(font, "Count", leftPos + 350, topPos + 170, 0xD9DEE8, false);
        guiGraphics.drawString(font, "Duration: 7 days. Listing fee disabled.", leftPos + 28, topPos + 235, 0xAEB7C4, false);
    }

    private void renderSystemPanel(GuiGraphics guiGraphics) {
        int panelX = leftPos + 30;
        int panelY = topPos + 62;
        int panelRight = leftPos + imageWidth - 30;
        int panelBottom = topPos + imageHeight - 48;
        int cardWidth = 60;
        int cardHeight = 85;
        int gap = 10;
        int columns = Math.max(1, (panelRight - panelX - 28 + gap) / (cardWidth + gap));
        guiGraphics.fill(panelX, panelY, panelRight, panelBottom, 0xCC15181D);

        if (selectedSystemOffer != null) {
            renderBuyConfirmation(guiGraphics);
            return;
        }

        int visibleIndex = 0;
        for (int i = 0; i < SystemMarketOffer.OFFERS.size(); i++) {
            SystemMarketOffer offer = SystemMarketOffer.OFFERS.get(i);
            if (!isSystemOfferVisible(offer)) {
                continue;
            }
            int col = visibleIndex % columns;
            int row = visibleIndex / columns;
            int x = panelX + 14 + col * (cardWidth + gap);
            int y = panelY + 14 + row * (cardHeight + gap);
            if (y + cardHeight > panelBottom - 8) {
                break;
            }
            guiGraphics.fill(x, y, x + cardWidth, y + cardHeight, 0x66323A42);
            renderScaledItem(guiGraphics, offer.item(), x + (cardWidth - 24) / 2, y + 10, 1.5F);
            drawCenteredString(guiGraphics, trim(offer.item().getHoverName().getString(), cardWidth - 8), x, y + 42, cardWidth, 0xF3F0E8);
            drawCenteredString(guiGraphics, offer.unitPrice() + " " + MarketEconomy.CURRENCY_NAME, x, y + 57, cardWidth, 0xD6E8D2);
            visibleIndex++;
        }
        if (visibleIndex == 0) {
            guiGraphics.drawString(font, "No system offers in this page yet.", panelX + 18, panelY + 70, 0xAEB7C4, false);
        }
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
        for (int i = 0; i < entries.size(); i++) {
            int col = i % 2;
            int row = i / 2;
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
        for (int i = 0; i < buyButtons.size(); i++) {
            boolean hasEntry = i < snapshot.entries().size();
            Button button = buyButtons.get(i);
            button.visible = !sellMode && !systemMode && !confirmingBuy && hasEntry;
            button.active = !sellMode && !systemMode && !confirmingBuy && hasEntry;
            if (button.visible) {
                int col = i % 2;
                int row = i / 2;
                button.setSize(42, 16);
                button.setX(leftPos + 200 + col * 244);
                button.setY(topPos + 43 + row * 42);
            }
        }
        if (previousButton != null) {
            previousButton.visible = !sellMode && !systemMode && !confirmingBuy;
            previousButton.active = !sellMode && !systemMode && !confirmingBuy && snapshot.page() > 1;
        }
        if (nextButton != null) {
            nextButton.visible = !sellMode && !systemMode && !confirmingBuy;
            nextButton.active = !sellMode && !systemMode && !confirmingBuy && snapshot.page() < snapshot.pageCount();
        }
        if (refreshButton != null) {
            refreshButton.visible = !sellMode && !systemMode && !confirmingBuy;
            refreshButton.active = !sellMode && !systemMode && !confirmingBuy;
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
            listTabButton.active = (sellMode || systemMode) && !confirmingBuy;
        }
        if (sellTabButton != null) {
            sellTabButton.active = !sellMode && !confirmingBuy;
        }
        if (systemTabButton != null) {
            systemTabButton.active = !systemMode && !confirmingBuy;
        }
        if (systemBuyTabButton != null) {
            systemBuyTabButton.visible = systemMode && !confirmingBuy;
            systemBuyTabButton.active = systemMode && systemSellPage && !confirmingBuy;
        }
        if (systemSellTabButton != null) {
            systemSellTabButton.visible = systemMode && !confirmingBuy;
            systemSellTabButton.active = systemMode && !systemSellPage && !confirmingBuy;
        }
        int visibleIndex = 0;
        int panelWidth = imageWidth - 60;
        int cardWidth = 60;
        int gap = 10;
        int columns = Math.max(1, (panelWidth - 28 + gap) / (cardWidth + gap));
        for (int i = 0; i < systemTradeButtons.size(); i++) {
            SystemMarketOffer offer = SystemMarketOffer.OFFERS.get(i);
            Button button = systemTradeButtons.get(i);
            boolean visible = systemMode && !confirmingBuy && isSystemOfferVisible(offer);
            button.visible = visible;
            button.active = visible;
            if (visible) {
                int col = visibleIndex % columns;
                int row = visibleIndex / columns;
                button.setSize(46, 14);
                button.setX(leftPos + 30 + 14 + col * (cardWidth + gap) + (cardWidth - 46) / 2);
                button.setY(topPos + 62 + 14 + row * (85 + gap) + 85 - 14 -3);
                visibleIndex++;
            }
        }
    }

    private void buy(int row) {
        List<MarketMenuEntry> entries = menu.snapshot().entries();
        if (row >= 0 && row < entries.size()) {
            openBuyConfirmation(entries.get(row));
        }
    }

    private void changePage(int delta) {
        MarketMenuSnapshot snapshot = menu.snapshot();
        int page = Math.max(1, Math.min(snapshot.page() + delta, snapshot.pageCount()));
        PacketDistributor.sendToServer(MarketActionPayload.refresh(page));
    }

    private void refresh() {
        PacketDistributor.sendToServer(MarketActionPayload.refresh(menu.snapshot().page()));
    }

    private void setSellMode(boolean sellMode) {
        this.sellMode = sellMode;
        this.systemMode = false;
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
        closeConfirmation();
        updateButtonStates();
    }

    private void setSystemSellPage(boolean systemSellPage) {
        this.systemSellPage = systemSellPage;
        closeConfirmation();
        updateButtonStates();
    }

    private void submitSell() {
        long price = parsePrice();
        int count = parseCount();
        if (price > 0L && count > 0) {
            PacketDistributor.sendToServer(MarketActionPayload.sell(price, count, menu.snapshot().page()));
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
            PacketDistributor.sendToServer(MarketActionPayload.buy(selectedBuyEntry.id(), count, menu.snapshot().page()));
        } else {
            PacketDistributor.sendToServer(MarketActionPayload.systemTrade(selectedSystemOffer.id(), count, menu.snapshot().page()));
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

    private String systemButtonText(SystemMarketOffer offer) {
        return offer.type() == SystemMarketOffer.Type.SYSTEM_SELLS ? "Buy" : "Sell";
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
        return systemSellPage
                ? offer.type() == SystemMarketOffer.Type.SYSTEM_BUYS
                : offer.type() == SystemMarketOffer.Type.SYSTEM_SELLS;
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

    private void renderScaledItem(GuiGraphics guiGraphics, ItemStack stack, int x, int y, float scale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0F);
        int scaledX = (int)(x / scale);
        int scaledY = (int)(y / scale);
        guiGraphics.renderItem(stack, scaledX, scaledY);
        guiGraphics.pose().popPose();
    }
}
