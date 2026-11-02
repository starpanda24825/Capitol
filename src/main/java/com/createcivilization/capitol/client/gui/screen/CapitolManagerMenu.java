package com.createcivilization.capitol.client.gui.screen;

import com.createcivilization.capitol.Capitol;
import com.createcivilization.capitol.client.gui.base.Asset;
import com.createcivilization.capitol.client.gui.base.BoundingBox;
import com.createcivilization.capitol.client.gui.base.BookScreen;
import com.createcivilization.capitol.client.gui.base.Interactable;
import com.createcivilization.capitol.client.gui.interactables.TextInputInteractable;
import com.createcivilization.capitol.client.networking.ClientBookState;
import com.createcivilization.capitol.client.networking.ClientTeamDirectoryCache;
import com.createcivilization.capitol.common.data.Permission;
import com.createcivilization.capitol.common.networking.packets.C2SRequestTeamDirectory;
import com.createcivilization.capitol.common.networking.packets.S2CTeamDirectory;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/*
 * The per-player manager, opened by '/capitol menu' (and keybind) rather than by a capitol block. 
 * Everything the book is built from lives in one texture sheet; of which include the slices of book_layout.png
 * it draws, the sections and their ribbons, the ribbon row along the page's top edge, and the team section's contents.
 */
public class CapitolManagerMenu extends BookScreen {

	//the sheet

	public static final Asset SHEET = new Asset(
		ResourceLocation.fromNamespaceAndPath(Capitol.MOD_ID, "textures/gui/book_layout.png"), 256, 256);

	private static final int PAGE_WIDTH = 149;
	private static final int PAGE_HEIGHT = 241;

	// P - the tall page, its own tight box on the sheet.
	private static final Asset.Blit PAGE = SHEET.blit(1, 1, PAGE_WIDTH, PAGE_HEIGHT);

	//The page's own border lines sit 2px in, so content starts just inside them.
	private static final int PAGE_INSET = 5;

	// the sheet's colours, indexed 0..4

	// one index per colour for whole sheet: verticals at column 151+19i and horizontal
	// bars on row 130+15i
	private static final int COLOUR_COLUMN = 151;
	private static final int COLOUR_PITCH = 19;
	private static final int BAR_U = 151;
	private static final int BAR_V = 130;
	private static final int BAR_PITCH = 15;
	private static final int BAR_HEIGHT = 14;

	// the (vertical?) hanging ribbons from the top of the page

	private static final int RIBBON_WIDTH = 18;
	private static final int SMALL_V = 75;
	private static final int SMALL_H = 31;
	private static final int LARGE_V = 1;
	private static final int LARGE_H = 73;
	private static final int RIBBON_PITCH = 22;

	/**
	 * This is how far (when player's mouse hovers over a ribbon) it extends. 
	 * The art is not re-cut: the same blit is drawn 3px longer from there, so a
	 * hanging ribbon grows downward and a horizontal one grows to the right.
	 */
	private static final int HOVER_DROP = 3;

	private static final int LABEL_INK = 0xFFFFFF;

	//A ribbon, small while its section is closed and large while it is the open one.
	private static Asset.Blit ribbon(int colour, boolean large) {
		int column = COLOUR_COLUMN + COLOUR_PITCH * colour;
		return large
			? SHEET.blit(column, LARGE_V, RIBBON_WIDTH, LARGE_H)
			: SHEET.blit(column, SMALL_V, RIBBON_WIDTH, SMALL_H);
	}

	//horizontal ribbons

	private static final int FACE = 8;
	private static final int ROW_GAP = 4;

	// The horizontal ribbon art is cut, so that the forked bit is to its right.
	private static final int BAR_WIDTH = 65;

	//How wide a horizontal ribbon is, drawn stretched to this width.
	private static final int ROW_WIDTH = 105;

	private static Asset.Blit teamBar() {
		return SHEET.blit(BAR_U, BAR_V + BAR_PITCH * Section.TEAM.colour(), BAR_WIDTH, BAR_HEIGHT)
			.stretchedTo(ROW_WIDTH, BAR_HEIGHT);
	}

	private static void rowBlit(GuiGraphics guiGraphics, int x, int y, boolean hovered) {
		Asset.Blit bar = teamBar();
		if (hovered) bar.renderBlit(guiGraphics, x, y, ROW_WIDTH + HOVER_DROP, BAR_HEIGHT);
		else bar.renderBlit(guiGraphics, x, y);
	}

	//the sections

	/**
	 * One hanging ribbon. Declaration order is the column order, which puts the "Team" in the middle
	 * with "Members" and "Roles" to its left. {@code needed} is what the viewer must hold to see all 
	 * ribbons, so nobody sees a section they cannot use.
	 */
	private enum Section {

		MEMBERS("members", 0, Permission.KICK_MEMBERS, Permission.ASSIGN_ROLES, Permission.MANAGE_ROLES, Permission.INVITE_MEMBERS),
		ROLES("roles", 2, Permission.MANAGE_ROLES),
		TEAM("team", 1),
		MAIL("mail", 3),
		SUBCLAIMS("subclaims", 4);

		private final String id;
		private final int colour;
		private final Permission[] needed;

		Section(String id, int colour, Permission... needed) {
			this.id = id;
			this.colour = colour;
			this.needed = needed;
		}

		private int colour() {
			return colour;
		}

		// The column this section owns. Fixed, so hiding a gated ribbon cannot shuffle the row
		private int column() {
			return ordinal();
		}

		// Ungated sections are for everyone in the team; the rest need certain permissions.
		private boolean visible(long permissions) {
			if (needed.length == 0) return true;
			for (Permission permission : needed) {
				if (permission.hasPermission(permissions)) return true;
			}
			return false;
		}

		private Component title() {
			return Component.translatable("screen.capitol.book.tab." + id);
		}
	}

	//the screen

	private static Section lastOpen = Section.TEAM;

	private final List<Section> sections;
	private final SearchField search = new SearchField();
	private Section selected;
	private boolean membersRequested;

	public CapitolManagerMenu() {
		super(Component.translatable("screen.capitol.book.title"));
		long permissions = ClientBookState.permissions();
		this.sections = ClientBookState.hasTeam()
			? Arrays.stream(Section.values()).filter(section -> section.visible(permissions)).toList()
			: List.of();
		this.selected = sections.contains(lastOpen) ? lastOpen : sections.isEmpty() ? null : sections.getFirst();
	}

	/*
	 * Where the row starts, measured over every section rather than the visible ones: the row
	 * stays centred on the page and a hidden ribbon leaves its column empty.
	 */
	private int rowInset() {
		int count = Section.values().length;
		int rowWidth = count * RIBBON_WIDTH + (count - 1) * (RIBBON_PITCH - RIBBON_WIDTH);
		return (PAGE_WIDTH - rowWidth) / 2;
	}

	@Override
	protected Asset.Blit getBackgroundBlit() {
		return PAGE;
	}

	@Override
	protected void init() {
		super.init();
		// a fresh screen asks for the member snapshot it draws; a resize reuses the one it has
		if (ClientBookState.hasTeam() && !membersRequested) {
			membersRequested = true;
			PacketDistributor.sendToServer(new C2SRequestTeamDirectory());
		}
		rebuild();
	}

	// Keeps the interactables in step with the open section: the rows of tabs, plus the search field.
	private void rebuild() {
		getInteractableList().clear();
		for (Section section : sections) addInteractable(new RibbonTab(section));
		if (selected != Section.TEAM) return;
		search.init(this);
		addInteractable(search);
	}

	private void select(Section section) {
		if (section == selected) return;
		this.selected = section;
		lastOpen = section;
		rebuild();
	}

	@Override
	public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		if (sections.isEmpty()) {
			// no team, so no ribbons either: say why the page is bare
			Component note = Component.translatable("screen.capitol.book.no_team");
			guiGraphics.drawString(this.font, note, this.leftPos + (PAGE_WIDTH - this.font.width(note)) / 2,
				this.topPos + PAGE_HEIGHT / 2, LABEL_INK, true);
		} else if (selected == Section.TEAM) {
			renderMembers(guiGraphics, rowAt(mouseX, mouseY));
		}
	}

	//the team section

	// The search bar sits clear of the longest ribbon; the rows start just under it.
	private static int searchY() {
		return LARGE_H + 7;
	}

	private static int listY() {
		return searchY() + BAR_HEIGHT + ROW_GAP + 1;
	}

	private int rowAt(double mouseX, double mouseY) {
		int x = this.leftPos;
		if (mouseX < x || mouseX >= x + ROW_WIDTH + HOVER_DROP) return -1;
		int bottom = this.topPos + PAGE_HEIGHT - PAGE_INSET;
		int y = this.topPos + listY();
		List<S2CTeamDirectory.MemberRow> rows = members();
		for (int i = 0; i < rows.size() && y + BAR_HEIGHT <= bottom; i++, y += BAR_PITCH) {
			if (mouseY >= y && mouseY < y + BAR_HEIGHT) return i;
		}
		return -1;
	}

	// Everyone in the team, alphabetical and narrowed by whatever is typed in the search bar.
	private List<S2CTeamDirectory.MemberRow> members() {
		S2CTeamDirectory directory = ClientTeamDirectoryCache.directory();
		if (directory == null) return List.of();

		String query = search.getValue().trim().toLowerCase(Locale.ROOT);
		return directory.members().stream()
			.filter(member -> query.isEmpty() || member.name().toLowerCase(Locale.ROOT).contains(query))
			.sorted(Comparator.comparing(member -> member.name().toLowerCase(Locale.ROOT)))
			.toList();
	}

	private void renderMembers(GuiGraphics guiGraphics, int hovered) {
		int x = this.leftPos;
		int bottom = this.topPos + PAGE_HEIGHT - PAGE_INSET;
		int y = this.topPos + listY();
		List<S2CTeamDirectory.MemberRow> rows = members();

		for (int i = 0; i < rows.size(); i++, y += BAR_PITCH) {
			if (y + BAR_HEIGHT > bottom) break;  // ran out of page, the rest is for a later page of the list
			rowBlit(guiGraphics, x, y, i == hovered);
			renderMember(guiGraphics, rows.get(i), x, y);
		}
	}

	// horizontal ribbon: player skin face and name, centred inside its ribbon.
	private void renderMember(GuiGraphics guiGraphics, S2CTeamDirectory.MemberRow member, int x, int y) {
		// centred on the resting row, so the 3px the row reaches into on hover moves the bar only
		String name = this.font.plainSubstrByWidth(member.name(), ROW_WIDTH - FACE - 2 * ROW_GAP - 8);
		int content = FACE + ROW_GAP + this.font.width(name);
		int faceX = x + (ROW_WIDTH - content) / 2;
		int textY = y + (BAR_HEIGHT - this.font.lineHeight) / 2 + 1;

		renderFace(guiGraphics, member.uuid(), faceX, y + (BAR_HEIGHT - FACE) / 2);
		guiGraphics.drawString(this.font, name, faceX + FACE + ROW_GAP, textY, LABEL_INK, true);
	}

	// The account's own skin when the client has it, and a blank when it does not.
	private static void renderFace(GuiGraphics guiGraphics, UUID player, int x, int y) {
		PlayerInfo info = Minecraft.getInstance().getConnection() == null
			? null
			: Minecraft.getInstance().getConnection().getPlayerInfo(player);
		ResourceLocation skin = info == null ? null : info.getSkin().texture();
		if (skin == null) {
			guiGraphics.fill(x, y, x + FACE, y + FACE, 0xFF555555);
			return;
		}
		guiGraphics.blit(skin, x, y, 8, 8, FACE, FACE, 64, 64);
	}

	// The team section's search bar: the same flat-ended row as the members, with a caret on it.
	private final class SearchField extends TextInputInteractable {

		private SearchField() {
			super(teamBar(), Component.translatable("screen.capitol.book.search"), 0, 0, null, null, true);
		}

		@Override
		public int getX() {
			return leftPos;
		}

		@Override
		public int getY() {
			return topPos + searchY();
		}

		@Override
		public void setX(int x) {}

		@Override
		public void setY(int y) {}
	}

	//the ribbons

	// One ribbon of the row. Small when its section is closed, large when open
	private final class RibbonTab implements Interactable.BlitInteractable {

		private final Section section;
		private boolean hovered;
		private boolean hidden;

		private RibbonTab(Section section) {
			this.section = section;
		}

		// The open section wears the large ribbon, so it is already pulled out and needs no tooltip.
		private boolean isLarge() {
			return section == selected;
		}

		private boolean isLowered() {
			return hovered && !isLarge();
		}

		@Override
		public int getX() {
			return leftPos + rowInset() + section.column() * RIBBON_PITCH;
		}

		@Override
		public int getY() {
			return topPos;
		}

		@Override
		public void setX(int x) {}

		@Override
		public void setY(int y) {}

		@Override
		public Asset.Blit getBlit() {
			return ribbon(section.colour(), isLarge());
		}

		// the box never moves, so hovering the ribbon's own top edge or the 3px it reaches into
		// cannot flicker it; a lowered ribbon keeps that extra 3px under the cursor
		@Override
		public BoundingBox getBoundingBox() {
			int x = getX();
			int height = isLarge() ? LARGE_H : SMALL_H + HOVER_DROP;
			return new BoundingBox(x, topPos, x + RIBBON_WIDTH, topPos + height);
		}

		@Override
		public void render(GuiGraphics guiGraphics) {
			if (isHidden()) return;
			Asset.Blit blit = getBlit();
			if (isLowered()) {
				// sliding the art bodily down would tear it off the page's top edge, so the ribbon
				// keeps its top and grows the 3px downward instead
				blit.renderBlit(guiGraphics, getX(), topPos, RIBBON_WIDTH, blit.getBlitHeight() + HOVER_DROP);
			} else {
				blit.renderBlit(guiGraphics, getX(), topPos);
			}
			if (isLarge()) renderLabel(guiGraphics);
		}

		//The section's name, rotated to run bottom to top along the ribbon.
		private void renderLabel(GuiGraphics guiGraphics) {
			Component label = section.title();
			int width = font.width(label);

			guiGraphics.pose().pushPose();
			guiGraphics.pose().translate(getX() + RIBBON_WIDTH / 2f, topPos + LARGE_H / 2f, 0f);
			guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(-90f));
			guiGraphics.drawString(font, label, -width / 2, -font.lineHeight / 2, LABEL_INK, true);
			guiGraphics.pose().popPose();
		}

		@Override
		public void hovered(int x, int y) {
			if (isHidden()) return;
			hovered = true;
		}

		@Override
		public void hoverLeave(int x, int y) {
			hovered = false;
		}

		@Override
		public Interactable clickStart(int x, int y) {
			if (isHidden()) return null;
			select(section);
			return this;
		}

		@Override
		public @Nullable Component tooltip() {
			return isLarge() ? null : section.title();
		}

		@Override
		public boolean isHidden() {
			return hidden;
		}

		@Override
		public void show() {
			hidden = false;
		}

		@Override
		public void hide() {
			hidden = true;
		}
	}
}
