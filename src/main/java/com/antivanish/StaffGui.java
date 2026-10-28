package com.antivanish;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Menu (tecla J) para gestionar la lista de staff. */
public class StaffGui extends GuiScreen {
    private static final int ROWS = 8;
    private static final int ROW_H = 12;
    private GuiTextField input;
    private List<String> names = new ArrayList<String>();
    private int selected = -1;
    private int scroll = 0;
    private int listLeft, listTop;
    private final int listWidth = 200;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        listLeft = width / 2 - 100;
        listTop = height / 2 - 35;
        input = new GuiTextField(10, fontRendererObj, listLeft, height / 2 - 65, 140, 20);
        input.setMaxStringLength(16);
        input.setFocused(true);
        buttonList.add(new GuiButton(1, listLeft + 145, height / 2 - 65, 55, 20, "A\u00f1adir"));
        buttonList.add(new GuiButton(2, listLeft, listTop + ROWS * ROW_H + 8, 98, 20, "Quitar"));
        buttonList.add(new GuiButton(3, listLeft + 102, listTop + ROWS * ROW_H + 8, 98, 20, "Cerrar"));
        reload();
    }

    private void reload() {
        names = StaffManager.getAll();
        if (selected >= names.size()) selected = -1;
        clampScroll();
    }

    private void clampScroll() {
        int max = Math.max(0, names.size() - ROWS);
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
    }

    private void addName() {
        if (StaffManager.add(input.getText())) {
            input.setText("");
            reload();
        }
    }

    private void removeSelected() {
        if (selected >= 0 && selected < names.size()) {
            StaffManager.remove(names.get(selected));
            selected = -1;
            reload();
        }
    }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        if (b.id == 1) addName();
        else if (b.id == 2) removeSelected();
        else if (b.id == 3) mc.displayGuiScreen(null);
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) { super.keyTyped(c, key); return; }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { addName(); return; }
        if (key == Keyboard.KEY_DELETE && !input.isFocused()) { removeSelected(); return; }
        input.textboxKeyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        super.mouseClicked(mx, my, btn);
        input.mouseClicked(mx, my, btn);
        if (btn == 0 && mx >= listLeft && mx <= listLeft + listWidth
                && my >= listTop && my < listTop + ROWS * ROW_H) {
            int idx = (my - listTop) / ROW_H + scroll;
            selected = idx < names.size() ? idx : -1;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) { scroll += d > 0 ? -1 : 1; clampScroll(); }
    }

    @Override
    public void updateScreen() {
        input.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "AntiVanish - Lista de staff", width / 2, height / 2 - 90, 0xFFFFFF);
        input.drawTextBox();

        drawRect(listLeft - 1, listTop - 1, listLeft + listWidth + 1, listTop + ROWS * ROW_H + 1, 0xAA000000);
        for (int i = 0; i < ROWS; i++) {
            int idx = i + scroll;
            if (idx >= names.size()) break;
            int y = listTop + i * ROW_H;
            if (idx == selected) drawRect(listLeft, y, listLeft + listWidth, y + ROW_H, 0x66FFFFFF);
            drawString(fontRendererObj, names.get(idx), listLeft + 4, y + 2, 0xFFFFFF);
        }
        if (names.isEmpty()) {
            drawString(fontRendererObj, "Sin nombres a\u00f1adidos", listLeft + 4, listTop + 2, 0x888888);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
