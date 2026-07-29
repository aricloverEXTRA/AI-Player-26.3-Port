package net.shasankp000.GraphicalUserInterface.Widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class DropdownMenuWidget extends ClickableWidget {
    private List<String> options;
    private boolean isOpen;
    private String selectedOption = "";
    private int selectedIndex = -1;
    private int hoveredIndex = -1;
    private final int rowHeight = 14;
    private final int maxVisibleOptions = 10;

    public DropdownMenuWidget(int x, int y, int width, int height, Text message, List<String> options) {
        super(x, y, width, height, message);
        this.options = (options != null) ? new ArrayList<>(options) : new ArrayList<>();
        this.isOpen = false;
    }

    public boolean isExpanded() {
        return this.isOpen;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        int buttonColor = this.isHovered() ? 0xFF404040 : 0xFF202020;
        context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFFFFFFFF);
        context.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1, buttonColor);

        String label = !selectedOption.isBlank() ? selectedOption : getMessage().getString();
        String fittedLabel = fitTextToWidth(tr, label, this.width - 10);
        context.drawCenteredTextWithShadow(tr, fittedLabel,
                this.getX() + this.width / 2,
                this.getY() + (this.height - 8) / 2,
                0xFFFFFFFF);

        if (isOpen && !options.isEmpty()) {
            context.createNewRootLayer();

            int listSize = Math.min(options.size(), maxVisibleOptions);
            int totalListHeight = listSize * rowHeight;

            context.fill(this.getX(), this.getY() + this.height,
                    this.getX() + this.width, this.getY() + this.height + totalListHeight, 0xFF101010);
            context.drawBorder(this.getX(), this.getY() + this.height, this.width, totalListHeight, 0xFFFFFFFF);

            for (int i = 0; i < listSize; i++) {
                int optionY = this.getY() + this.height + (i * rowHeight);
                boolean isMouseOverOption = mouseX >= this.getX() && mouseX < this.getX() + this.width
                        && mouseY >= optionY && mouseY < optionY + rowHeight;

                if (isMouseOverOption || i == hoveredIndex) {
                    context.fill(this.getX() + 1, optionY,
                            this.getX() + this.width - 1, optionY + rowHeight, 0xFF3366FF);
                    if (isMouseOverOption) {
                        this.hoveredIndex = i;
                    }
                }

                context.drawTextWithShadow(tr, options.get(i),
                        this.getX() + 5, optionY + 3, 0xFFFFFFFF);
            }
        }
    }

    private static String fitTextToWidth(TextRenderer tr, String text, int maxWidth) {
        if (tr.getWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int allowedWidth = Math.max(0, maxWidth - tr.getWidth(ellipsis));
        return tr.trimToWidth(text, allowedWidth) + ellipsis;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (super.isMouseOver(mouseX, mouseY)) {
            return true;
        }
        if (!this.isOpen || options.isEmpty()) {
            return false;
        }
        int listSize = Math.min(options.size(), maxVisibleOptions);
        int listTop = this.getY() + this.height;
        int listBottom = listTop + listSize * rowHeight;
        return mouseX >= this.getX() && mouseX < this.getX() + this.width
                && mouseY >= listTop && mouseY < listBottom;
    }

    @Override
    public boolean isHovered() {
        return this.isMouseOver(
                MinecraftClient.getInstance().mouse.getX() * MinecraftClient.getInstance().getWindow().getScaledWidth()
                        / (double) MinecraftClient.getInstance().getWindow().getWidth(),
                MinecraftClient.getInstance().mouse.getY() * MinecraftClient.getInstance().getWindow().getScaledHeight()
                        / (double) MinecraftClient.getInstance().getWindow().getHeight());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible || button != 0) return false;

        boolean clickedMain = mouseX >= this.getX() && mouseX < this.getX() + this.width
                && mouseY >= this.getY() && mouseY < this.getY() + this.height;

        if (isOpen) {
            int listSize = Math.min(options.size(), maxVisibleOptions);
            for (int i = 0; i < listSize; i++) {
                int optionY = this.getY() + this.height + (i * rowHeight);

                if (mouseX >= this.getX() && mouseX <= this.getX() + this.width
                        && mouseY >= optionY && mouseY < optionY + rowHeight) {

                    this.selectedIndex = i;
                    this.selectedOption = options.get(i);
                    this.isOpen = false;
                    this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                    return true;
                }
            }
            this.isOpen = false;
            return true;
        }

        if (clickedMain) {
            this.isOpen = !this.isOpen;
            if (this.isOpen) {
                this.hoveredIndex = options.isEmpty() ? -1 : 0;
            }
            this.playDownSound(MinecraftClient.getInstance().getSoundManager());
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.active || !this.visible || !this.isOpen) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        int listSize = Math.min(options.size(), maxVisibleOptions);
        if (listSize == 0) return super.keyPressed(keyCode, scanCode, modifiers);

        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            this.hoveredIndex = (this.hoveredIndex + 1) % listSize;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_UP) {
            this.hoveredIndex = (this.hoveredIndex - 1 + listSize) % listSize;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (hoveredIndex >= 0 && hoveredIndex < options.size()) {
                this.selectedIndex = hoveredIndex;
                this.selectedOption = options.get(hoveredIndex);
                this.isOpen = false;
                this.playDownSound(MinecraftClient.getInstance().getSoundManager());
            }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.isOpen = false;
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, getMessage());
    }

    public void updateOptions(List<String> newOptions) {
        this.options = new ArrayList<>(newOptions != null ? newOptions : List.of());
        this.hoveredIndex = this.options.isEmpty() ? -1 : 0;
        this.selectedIndex = this.options.indexOf(this.selectedOption);
        if (this.selectedIndex < 0) {
            this.selectedOption = "";
        }
    }

    public String getSelectedOption() {
        return selectedOption != null ? selectedOption : "";
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }
}