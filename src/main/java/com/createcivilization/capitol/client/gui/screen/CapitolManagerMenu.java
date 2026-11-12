package com.createcivilization.capitol.client.gui.screen;

import com.createcivilization.capitol.Capitol;
import com.createcivilization.capitol.client.gui.base.Asset;
import com.createcivilization.capitol.client.gui.base.BoundingBox;
import com.createcivilization.capitol.client.gui.base.BookScreen;
import com.createcivilization.capitol.client.gui.base.Interactable;
import com.createcivilization.capitol.client.gui.interactables.TextInputInteractable;
import com.createcivilization.capitol.client.networking.ClientBookState;
import com.createcivilization.capitol.client.networking.ClientSubClaimCache;
import com.createcivilization.capitol.client.networking.ClientTeamDirectoryCache;
import com.createcivilization.capitol.common.data.Permission;
import com.createcivilization.capitol.common.data.SubClaimSummary;
import com.createcivilization.capitol.common.data.TeamSubTab;
import com.createcivilization.capitol.common.networking.packets.C2SCreateSubTab;
import com.createcivilization.capitol.common.networking.packets.C2SRenameSubTab;
import com.createcivilization.capitol.common.networking.packets.C2SDeleteSubTab;
import com.createcivilization.capitol.common.networking.packets.C2SRequestSubClaims;
import com.createcivilization.capitol.common.networking.packets.C2SRequestTeamDirectory;
import com.createcivilization.capitol.common.networking.packets.S2CSubClaimsPage;
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
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
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
	private static final int BAR_WIDTH = 65; // The horizontal ribbon art is cut, so that the forked bit is to its right.
	private static final int ROW_WIDTH = 105; 	//How wide a horizontal ribbon is, drawn stretched to this width.
	private static final int ROW_LEFT = -1; // how faer the horizontal ribbons should start from the left of the page.

	// search field colour customisation

	private static final int SEARCH_FILL = 0xFFF5E7C8; // the page's parchment, a shade lighter
	private static final int SEARCH_EDGE = 0xFF4C4940; // the darker frame
	private static final int SEARCH_INK = 0xFF3B3630; // for font
	// a 105x14 patch: this is only a hit-box.
	private static final int BLANK_U = 1;
	private static final int BLANK_V = 242;

	//how the horizontal ribbon is cut from the texture sheet
	private static Asset.Blit bar(int colour) {
		return SHEET.blit(BAR_U, BAR_V + BAR_PITCH * colour, BAR_WIDTH, BAR_HEIGHT)
			.stretchedTo(ROW_WIDTH, BAR_HEIGHT);
	}

	// in screen coordinates of the horizontal ribbons and the search field.
	private int rowLeft() {
		return this.leftPos + ROW_LEFT;
	}

	// How the ribbon extends when the mouse is hovering over it.
	private static void rowBlit(GuiGraphics guiGraphics, int x, int y, boolean hovered, int colour) {
		Asset.Blit bar = bar(colour);
		if (hovered) bar.renderBlit(guiGraphics, x, y, ROW_WIDTH + HOVER_DROP, BAR_HEIGHT);
		else bar.renderBlit(guiGraphics, x, y);
	}

	//the Team section's sub-tabs

	// The corner piece, cut once per colour.
	private static final int CAP_U = 217;
	private static final int CAP_V = 130;
	private static final int CAP_ART_W = 20;
	private static final int CAP_ART_PITCH = 14;
	private static final int CAP_WIDTH = 29;
	private static final int CAP_HEIGHT = 13;
	private static final int CAP_STEP = 22;		// How far apart two tabs start.
	private static final int CAP_LIFT = 3;		// How far an open tab extends
	// The Sub-claim section's two default tabs, black for team's and gold for your own.
	private static final int SUBTAB_ALL = 0;
	private static final int SUBTAB_MINE = 1;
	private static final int CAP_BLACK = 3;
	private static final int CAP_YELLOW = 4;
	// The Team section's own three tabs, shown only to viewers who may edit the team.
	private static final int TEAM_TAB_MEMBERS = 0;
	private static final int TEAM_TAB_TEAM = 1;
	private static final int TEAM_TAB_CLAIMS = 2;
	// The Mail section's two fixed tabs; fixed for everyone.
	private static final int MAIL_TAB_INBOX = 0;
	private static final int MAIL_TAB_SEND = 1;
	private static final int PLUS_WIDTH = 13;		// The add button at the edge of the ribbon.
	private static final int PLUS_GAP = 8;

	//the add-sub-tab dialog 

	private static final int DIALOG_WIDTH = 121;
	private static final int DIALOG_HEIGHT = 102;
	private static final int DIALOG_TOP = 62;		// Just above centre, so its a page of its own laid over the manager
	private static final int DIALOG_HEIGHT_EDIT = 120; //for Edit/Delete buttons on the dialog box pop-up.
	private static final int SWATCH_STEP = 23;
	private static final int BUTTON_HEIGHT = 14;
	private static final int DIALOG_DIM = 0x80000000;	// aesthetic dimming of the manager behind the dialog panel, to keep it in focus

	// A cap piece of one colour, drawn at the given width
	private static Asset.Blit cap(int colour, int width) {
		return SHEET.blit(CAP_U, CAP_V + CAP_ART_PITCH * colour, CAP_ART_W, CAP_HEIGHT)
			.stretchedTo(width, CAP_HEIGHT);
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
	private final SearchField search = new SearchField("screen.capitol.book.search");
	private final SearchField subClaimSearch = new SearchField("screen.capitol.book.search_subclaims");
	private Section selected;
	private boolean membersRequested;
	private int openSubTab = -1; //which sub-tab is open, or -1 for member list. 0 is team's sub-claims, 1 is player's own, and 2 upwards are custom tabs.
	private int teamSubTab = TEAM_TAB_MEMBERS;	// Which of the Team section's own three tabs is open; always one, and the member list by default.
	private int mailSubTab = MAIL_TAB_INBOX;	// Which of the Mail section's two fixed tabs is open, or -1 for none

	/**
	 * The list the open tab last asked the server for, as "who|name filter": the team's list and the
	 * player's own are two different reads, and a custom tab narrows the team's by its name. Will change later.
	 */
	@Nullable
	private String requestedSubClaimList;
	// How many custom tabs the row was built for, so one arriving from the server can add itself.
	private int builtTabCount = -1;
	private boolean dialogOpen;
	private int dialogColour = CAP_BLACK;
	/** The custom tab the dialog edits, or null while it is making a new one. */
	@Nullable
	private TeamSubTab dialogTab;
	/** True once the dialog has been asked to delete, so its buttons become the confirmation. */
	private boolean deleteConfirm;
	@Nullable
	private DialogNameField dialogName;

	public CapitolManagerMenu() {
		super(Component.translatable("screen.capitol.book.title"));
		long permissions = ClientBookState.permissions();
		this.sections = ClientBookState.hasTeam()
			? Arrays.stream(Section.values()).filter(section -> section.visible(permissions)).toList()
			: List.of();
		this.selected = sections.contains(lastOpen) ? lastOpen : sections.isEmpty() ? null : sections.getFirst();
		// the sub-claim section opens on the team's own list, so it is never a bare page on the first open
		if (this.selected == Section.SUBCLAIMS) this.openSubTab = SUBTAB_ALL;
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

		if (selected == Section.MAIL) {
			addMailTabs();
			return;
		}

		if (selected == Section.SUBCLAIMS) {
			builtTabCount = subTabs().size();
			addSubTabs();
			requestSubClaims();
			if (dialogOpen) {
				// the dialog is modal while it is up, so nothing else on the page takes a click. The field
				// steps out for the delete confirmation, where only the two buttons belong.
				addInteractable(new SubTabDialog());
				if (dialogName != null && !deleteConfirm) {
					dialogName.init(this);
					addInteractable(dialogName);
				}
				return;
			}
		} else if (selected == Section.TEAM) {
			if (teamHasTabs()) {
				addTeamTabs();
				// only the member list holds rows and a field, so the other two tabs stop here
				if (teamSubTab != TEAM_TAB_MEMBERS) return;
			}
		} else {
			return;
		}

		SearchField field = searchField();
		field.init(this);
		addInteractable(field);
	}

	// builds the sub-tab row, and the add chip while the team has room for one
	private void addSubTabs() {
		List<TeamSubTab> customs = subTabs();
		List<Interactable> row = new ArrayList<>();
		if (plusIndex() >= 0) row.add(new TabPlus(plusIndex()));
		for (int i = 0; i < customs.size(); i++) {
			int index = 2 + i;
			TeamSubTab tab = customs.get(i);
			// a custom tab is the only editable kind, so it is the only one with a right-click action
			row.add(new CornerTab(index, subTabColour(index), Component.literal(tab.name()),
				index == openSubTab, () -> selectSubTab(index), () -> openEditDialog(index)));
		}
		row.add(new CornerTab(SUBTAB_MINE, subTabColour(SUBTAB_MINE),
			Component.translatable("screen.capitol.book.subtab.mine"), SUBTAB_MINE == openSubTab,
			() -> selectSubTab(SUBTAB_MINE), null));
		row.add(new CornerTab(SUBTAB_ALL, subTabColour(SUBTAB_ALL),
			Component.translatable("screen.capitol.book.subtab.all"), SUBTAB_ALL == openSubTab,
			() -> selectSubTab(SUBTAB_ALL), null));
		// left-most last, so no piece can end up drawn over the tab to its right
		row.sort(Comparator.comparingInt(Interactable::getX).reversed());
		row.forEach(this::addInteractable);
	}

	// The Mail section's two fixed tabs neither of them editable.
	private void addMailTabs() {
		addTabRow(List.of(
			tab(MAIL_TAB_INBOX, CAP_BLACK, "screen.capitol.book.mail_tab.inbox",
				mailSubTab == MAIL_TAB_INBOX, () -> selectMailSubTab(MAIL_TAB_INBOX)),
			tab(MAIL_TAB_SEND, CAP_BLACK, "screen.capitol.book.mail_tab.send",
				mailSubTab == MAIL_TAB_SEND, () -> selectMailSubTab(MAIL_TAB_SEND))));
	}

	// The Team section's own three tabs, all in the Team ribbon's colour, none of them editable.
	private void addTeamTabs() {
		addTabRow(List.of(
			tab(TEAM_TAB_MEMBERS, Section.TEAM.colour(), "screen.capitol.book.team_tab.members",
				teamSubTab == TEAM_TAB_MEMBERS, () -> selectTeamSubTab(TEAM_TAB_MEMBERS)),
			tab(TEAM_TAB_TEAM, Section.TEAM.colour(), "screen.capitol.book.team_tab.team",
				teamSubTab == TEAM_TAB_TEAM, () -> selectTeamSubTab(TEAM_TAB_TEAM)),
			tab(TEAM_TAB_CLAIMS, Section.TEAM.colour(), "screen.capitol.book.team_tab.claims",
				teamSubTab == TEAM_TAB_CLAIMS, () -> selectTeamSubTab(TEAM_TAB_CLAIMS))));
	}

	private CornerTab tab(int index, int colour, String labelKey, boolean open, Runnable onClick) {
		return new CornerTab(index, colour, Component.translatable(labelKey), open, onClick, null);
	}

	private void addTabRow(List<CornerTab> tabs) {
		List<Interactable> row = new ArrayList<>(tabs);
		row.sort(Comparator.comparingInt(Interactable::getX).reversed());
		row.forEach(this::addInteractable);
	}

	
	// opens a sub-tab, or closes it when it is the one already open
	private void selectSubTab(int index) {
		openSubTab = openSubTab == index ? -1 : index;
		// a filter typed for one list must not thin the next one out
		search.setValue("");
		subClaimSearch.setValue("");
		rebuild();
	}

	// The Team tabs never close: one is always open, and the member list is the default.
	private void selectTeamSubTab(int index) {
		if (teamSubTab == index) return;
		teamSubTab = index;
		rebuild();
	}

	// The Mail tabs toggle between the two
	private void selectMailSubTab(int index) {
		mailSubTab = mailSubTab == index ? -1 : index;
		rebuild();
	}

	// the field filtering whichever list is showing
	private SearchField searchField() {
		return selected == Section.SUBCLAIMS ? subClaimSearch : search;
	}

	// The add button on the ribbon, or -1 while they still have all three customs.
	private int plusIndex() {
		int customs = subTabs().size();
		return customs < TeamSubTab.MAX_PER_PLAYER ? customs + 2 : -1;
	}


	private void openDialog() {
		dialogOpen = true;
		dialogTab = null;
		deleteConfirm = false;
		dialogColour = CAP_BLACK;
		dialogName = new DialogNameField();
		rebuild();
	}

	/** Opens the same dialog on an existing custom tab, prefilled so it can be renamed or deleted. */
	private void openEditDialog(int index) {
		TeamSubTab tab = customTab(index);
		if (tab == null) return;
		dialogOpen = true;
		dialogTab = tab;
		deleteConfirm = false;
		dialogColour = tab.colour();
		dialogName = new DialogNameField();
		dialogName.setValue(tab.name());
		rebuild();
	}

	private void closeDialog() {
		dialogOpen = false;
		dialogTab = null;
		deleteConfirm = false;
		dialogName = null;
		rebuild();
	}

	// asks for the open tab's list, and only again when the list itself changes
	private void requestSubClaims() {
		if (openSubTab < 0) return;
		boolean mine = openSubTab == SUBTAB_MINE;
		TeamSubTab custom = customTab(openSubTab);
		String query = custom == null ? "" : custom.name();
		String key = (mine ? "mine|" : "team|") + query;
		if (key.equals(requestedSubClaimList)) return;
		requestedSubClaimList = key;
		UUID self = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
		if (mine && self == null) return;
		PacketDistributor.sendToServer(new C2SRequestSubClaims(0, !mine, mine ? self.toString() : "", query));
	}

	private void select(Section section) {
		if (section == selected) return;
		this.selected = section;
		lastOpen = section;
		// the sub-claim section lands on the team's list rather than a bare page of tabs
		if (section == Section.SUBCLAIMS && openSubTab < 0) openSubTab = SUBTAB_ALL;
		rebuild();
	}

	// Escape closes the dialog first, second closes the manager
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (dialogOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
			closeDialog();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
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
			// only the member list has rows to draw; the Team and Claims tabs hold nothing yet, and a
			// page with nothing on it is left bare rather than written on
			if (teamHasTabs() && teamSubTab != TEAM_TAB_MEMBERS) return;
			renderMembers(guiGraphics, rowAt(mouseX, mouseY));
		} else if (selected == Section.SUBCLAIMS) {
			// a new tab arrives from the server, so the row follows the directory
			if (subTabs().size() != builtTabCount) rebuild();
			if (dialogOpen) return;  // the dialog is modal: the page behind it stays bare
			renderSubClaims(guiGraphics, rowAt(mouseX, mouseY));
		}
		// the Mail tabs hold nothing yet, so their pages stay bare too
	}

	//the team section

	// clear of the longest ribbon; the search bar and rows start just under the sub-tabs
	private static int subTabY() {
		return LARGE_H + 7;
	}

	/** The Team section has no tab row above its field, so its bar and rows sit a row higher. */
	private static int teamSearchY() {
		return LARGE_H + 7;
	}

	private static int teamListY() {
		return teamSearchY() + BAR_HEIGHT + ROW_GAP + 1;
	}

	/**
	 * The field and rows of any section that carries a corner-tab row: one row under the tabs for the
	 * field, and the rows under that. The Team section borrows it too when its own tabs are shown.
	 */
	private static int tabbedSearchY() {
		return subTabY() + CAP_HEIGHT + ROW_GAP;
	}

	private static int tabbedListY() {
		return tabbedSearchY() + BAR_HEIGHT + ROW_GAP + 1;
	}

	/** Whether the open section draws a row of corner tabs, which pushes its content a row down. */
	private boolean hasTabRow() {
		return selected == Section.SUBCLAIMS || selected == Section.MAIL || teamHasTabs();
	}

	/**
	 * Whether the Team section shows its own three tabs. Everyone sees the member list; a viewer who
	 * may edit the team gets it as the first tab of a row, so the page can grow a Team and Claims tab.
	 */
	private boolean teamHasTabs() {
		return selected == Section.TEAM && Permission.MANAGE_TEAM.hasPermission(ClientBookState.permissions());
	}

	/** Where the open section's field sits, and where its rows start. */
	private int searchY() {
		return hasTabRow() ? tabbedSearchY() : teamSearchY();
	}

	private int listY() {
		return hasTabRow() ? tabbedListY() : teamListY();
	}

	private int rowAt(double mouseX, double mouseY) {
		int x = rowLeft();
		if (mouseX < x || mouseX >= x + ROW_WIDTH + HOVER_DROP) return -1;
		int bottom = this.topPos + PAGE_HEIGHT - PAGE_INSET;
		int y = this.topPos + listY();
		for (int i = 0; i < rowCount() && y + BAR_HEIGHT <= bottom; i++, y += BAR_PITCH) {
			if (mouseY >= y && mouseY < y + BAR_HEIGHT) return i;
		}
		return -1;
	}

	// the team's members, alphabetical and narrowed by the search field
	private List<S2CTeamDirectory.MemberRow> members() {
		S2CTeamDirectory directory = ClientTeamDirectoryCache.directory();
		if (directory == null) return List.of();

		String query = searchField().getValue().trim().toLowerCase(Locale.ROOT);
		return directory.members().stream()
			.filter(member -> query.isEmpty() || member.name().toLowerCase(Locale.ROOT).contains(query))
			.sorted(Comparator.comparing(member -> member.name().toLowerCase(Locale.ROOT)))
			.toList();
	}

	// the team's custom sub-tabs, in the server's order
	private List<TeamSubTab> subTabs() {
		S2CTeamDirectory directory = ClientTeamDirectoryCache.directory();
		return directory == null ? List.of() : directory.subTabs();
	}

	// the custom tab an index names, or null for the two defaults
	@Nullable
	private TeamSubTab customTab(int index) {
		List<TeamSubTab> tabs = subTabs();
		int custom = index - 2;
		return custom >= 0 && custom < tabs.size() ? tabs.get(custom) : null;
	}

	// the colour a sub-tab wears
	private int subTabColour(int index) {
		if (index == SUBTAB_ALL) return CAP_BLACK;
		if (index == SUBTAB_MINE) return CAP_YELLOW;
		TeamSubTab tab = customTab(index);
		return tab == null ? CAP_BLACK : tab.colour();
	}

	// the open tab's rows, held to the page's scope and narrowed like the members
	private List<SubClaimSummary> subClaims() {
		// no tab open: the section shows its row of tabs and nothing else
		if (openSubTab < 0) return List.of();
		S2CSubClaimsPage page = ClientSubClaimCache.lastPage();
		if (page == null) return List.of();
		List<SubClaimSummary> scoped = page.subClaims();
		if (openSubTab == SUBTAB_MINE) {
			UUID self = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
			scoped = scoped.stream().filter(row -> self != null && self.equals(row.ownerUuid())).toList();
		} else if (customTab(openSubTab) instanceof TeamSubTab tab) {
			scoped = scoped.stream().filter(row -> tab.matches(row.name())).toList();
		}

		String query = searchField().getValue().trim().toLowerCase(Locale.ROOT);
		return scoped.stream()
			.filter(row -> query.isEmpty() || row.name().toLowerCase(Locale.ROOT).contains(query))
			.sorted(Comparator.comparing(row -> row.name().toLowerCase(Locale.ROOT)))
			.toList();
	}

	// how many rows the open list has
	private int rowCount() {
		return selected == Section.SUBCLAIMS ? subClaims().size() : members().size();
	}

	private void renderMembers(GuiGraphics guiGraphics, int hovered) {
		int x = rowLeft();
		int bottom = this.topPos + PAGE_HEIGHT - PAGE_INSET;
		int y = this.topPos + listY();
		List<S2CTeamDirectory.MemberRow> rows = members();

		for (int i = 0; i < rows.size(); i++, y += BAR_PITCH) {
			if (y + BAR_HEIGHT > bottom) break;  // ran out of page, the rest is a later page
			rowBlit(guiGraphics, x, y, i == hovered, Section.TEAM.colour());
			renderMember(guiGraphics, rows.get(i), x, y);
		}
	}

	// a sub-claim tab's list: one name per row, in the tab's colour
	private void renderSubClaims(GuiGraphics guiGraphics, int hovered) {
		int x = rowLeft();
		int bottom = this.topPos + PAGE_HEIGHT - PAGE_INSET;
		int y = this.topPos + listY();
		List<SubClaimSummary> rows = subClaims();

		if (rows.isEmpty()) {
			Component note = Component.translatable("screen.capitol.book.subtab.empty");
			guiGraphics.drawString(this.font, note, x + (ROW_WIDTH - this.font.width(note)) / 2,
				y, SEARCH_INK, false);
			return;
		}

		for (int i = 0; i < rows.size(); i++, y += BAR_PITCH) {
			if (y + BAR_HEIGHT > bottom) break;  // ran out of page, the rest is a later page
			rowBlit(guiGraphics, x, y, i == hovered, subTabColour(openSubTab));
			// centred on the resting row, so hovering moves the bar and not the text
			String name = this.font.plainSubstrByWidth(rows.get(i).name(), ROW_WIDTH - 2 * ROW_GAP - 8);
			guiGraphics.drawString(this.font, name, x + (ROW_WIDTH - this.font.width(name)) / 2,
				y + (BAR_HEIGHT - this.font.lineHeight) / 2 + 1, LABEL_INK, true);
		}
	}

	// face and name, centred in the row
	private void renderMember(GuiGraphics guiGraphics, S2CTeamDirectory.MemberRow member, int x, int y) {
		// centred on the resting row, so hovering moves the bar and not the text
		String name = this.font.plainSubstrByWidth(member.name(), ROW_WIDTH - FACE - 2 * ROW_GAP - 8);
		int content = FACE + ROW_GAP + this.font.width(name);
		int faceX = x + (ROW_WIDTH - content) / 2;
		int textY = y + (BAR_HEIGHT - this.font.lineHeight) / 2 + 1;

		renderFace(guiGraphics, member.uuid(), faceX, y + (BAR_HEIGHT - FACE) / 2);
		guiGraphics.drawString(this.font, name, faceX + FACE + ROW_GAP, textY, LABEL_INK, true);
	}

	// the account's skin, or a blank when the client has none
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

	// The team section's search bar
	private final class SearchField extends TextInputInteractable {

		private SearchField(String placeholderKey) {
			super(SHEET.blit(BLANK_U, BLANK_V, ROW_WIDTH, BAR_HEIGHT), Component.translatable(placeholderKey),
				0, 0, null, SEARCH_INK, true);
		}

		@Override
		public int getX() {
			return rowLeft();
		}

		@Override
		public int getY() {
			return topPos + searchY();
		}

		@Override
		public void setX(int x) {}

		@Override
		public void setY(int y) {}

		@Override
		public void render(GuiGraphics guiGraphics) {
			int x = getX();
			int y = getY();
			// a one-pixel ink frame around a lighter parchment
			guiGraphics.fill(x, y, x + ROW_WIDTH, y + BAR_HEIGHT, SEARCH_EDGE);
			guiGraphics.fill(x + 1, y + 1, x + ROW_WIDTH - 1, y + BAR_HEIGHT - 1, SEARCH_FILL);
			super.render(guiGraphics);
		}
	}

	//sub-tabs

	// one sub tab, lifted while it is open or hovered
	private final class CornerTab implements Interactable {

		private final int index;
		private final int colour;
		private final Component label;
		private final boolean open;
		private final Runnable onClick;
		@Nullable
		private final Runnable onRightClick;
		private boolean hovered;

		private CornerTab(int index, int colour, Component label, boolean open, Runnable onClick,
						  @Nullable Runnable onRightClick) {
			this.index = index;
			this.colour = colour;
			this.label = label;
			this.open = open;
			this.onClick = onClick;
			this.onRightClick = onRightClick;
		}

		// the cap's flat end sits against the page, so it only reaches upward
		private boolean isLifted() {
			return open || hovered;
		}

		@Override
		public int getX() {
			return rowLeft() + index * CAP_STEP;
		}

		@Override
		public int getY() {
			return topPos + subTabY();
		}

		@Override
		public void setX(int x) {
		}

		@Override
		public void setY(int y) {
		}

		// the row is rebuilt, never hidden, so there is no hidden state
		@Override
		public void hide() {
		}

		@Override
		public void show() {
		}

		@Override
		public boolean isHidden() {
			return false;
		}

		// tabs overlap by 7px, so the box stops at the lap and no pixel answers for two
		@Override
		public BoundingBox getBoundingBox() {
			int x = getX();
			int y = getY();
			int left = index == 0 ? x : x + (CAP_WIDTH - CAP_STEP);
			return new BoundingBox(left, y - CAP_LIFT, x + CAP_WIDTH, y + CAP_HEIGHT);
		}

		@Override
		public void render(GuiGraphics guiGraphics) {
			Asset.Blit art = cap(colour, CAP_WIDTH);
			if (isLifted()) art.renderBlit(guiGraphics, getX(), getY() - CAP_LIFT, CAP_WIDTH, CAP_HEIGHT + CAP_LIFT);
			else art.renderBlit(guiGraphics, getX(), getY());
		}

		@Override
		public void hovered(int x, int y) {
			if (dialogOpen) return;
			hovered = true;
		}

		@Override
		public void hoverLeave(int x, int y) {
			hovered = false;
		}

		@Override
		public Interactable clickStart(int x, int y) {
			if (dialogOpen) return null;
			onClick.run();
			return this;
		}

		@Override
		public void rightClickStart(int x, int y) {
			if (dialogOpen || onRightClick == null) return;
			onRightClick.run();
		}

		@Override
		public @Nullable Component tooltip() {
			return dialogOpen ? null : label;
		}
	}

	// the add chip at the row's end; it opens the add-sub-tab dialog
	private final class TabPlus implements Interactable {

		private final int index;
		private boolean hovered;

		private TabPlus(int index) {
			this.index = index;
		}

		@Override
		public int getX() {
			return rowLeft() + index * CAP_STEP + PLUS_GAP;
		}

		@Override
		public int getY() {
			return topPos + subTabY() + (hovered ? -CAP_LIFT : 0);
		}

		@Override
		public void setX(int x) {
		}

		@Override
		public void setY(int y) {
		}

		// the row is rebuilt, never hidden, so there is no hidden state
		@Override
		public void hide() {
		}

		@Override
		public void show() {
		}

		@Override
		public boolean isHidden() {
			return false;
		}

		// the box holds still while the chip lifts
		@Override
		public BoundingBox getBoundingBox() {
			int x = getX();
			int y = topPos + subTabY();
			return new BoundingBox(x, y - CAP_LIFT, x + PLUS_WIDTH, y + CAP_HEIGHT);
		}

		@Override
		public void render(GuiGraphics guiGraphics) {
			int x = getX();
			int y = getY();
			guiGraphics.fill(x, y, x + PLUS_WIDTH, y + CAP_HEIGHT, SEARCH_EDGE);
			guiGraphics.fill(x + 1, y + 1, x + PLUS_WIDTH - 1, y + CAP_HEIGHT - 1, SEARCH_FILL);
			// the plus itself: two ink bars crossing at the centre
			int centreX = x + PLUS_WIDTH / 2;
			int centreY = y + CAP_HEIGHT / 2;
			guiGraphics.fill(centreX - 3, centreY, centreX + 4, centreY + 1, SEARCH_INK);
			guiGraphics.fill(centreX, centreY - 3, centreX + 1, centreY + 4, SEARCH_INK);
		}

		@Override
		public void hovered(int x, int y) {
			if (dialogOpen) return;
			hovered = true;
		}

		@Override
		public void hoverLeave(int x, int y) {
			hovered = false;
		}

		@Override
		public Interactable clickStart(int x, int y) {
			if (dialogOpen) return null;
			openDialog();
			return this;
		}

		@Override
		public @Nullable Component tooltip() {
			return dialogOpen ? null : Component.translatable("screen.capitol.book.subtab.add");
		}
	}

	//the "add-sub-tab" dialog

	// the add-sub-tab dialog; while it is up it covers the page and takes every click
	private final class SubTabDialog implements Interactable {

		// A tab is being ediTe
		private boolean editing() {
			return dialogTab != null;
		}

		private int dialogHeight() {
			return editing() ? DIALOG_HEIGHT_EDIT : DIALOG_HEIGHT;
		}

		private int panelX() {
			return leftPos + (PAGE_WIDTH - DIALOG_WIDTH) / 2;
		}

		private int panelY() {
			return topPos + DIALOG_TOP;
		}

		private int fieldX() {
			return panelX() + 4;
		}

		private int fieldY() {
			return panelY() + 20;
		}

		private int fieldWidth() {
			return DIALOG_WIDTH - 8;
		}

		private int swatchX(int colour) {
			return panelX() + 4 + colour * SWATCH_STEP;
		}

		private int swatchY() {
			return panelY() + 52;
		}

		private int buttonWidth() {
			return (DIALOG_WIDTH - 12) / 2;
		}

		private int buttonY() {
			return panelY() + 80;
		}

		// Delete
		private int dangerY() {
			return panelY() + 100;
		}

		// Add only goes live once a name is typed
		private boolean ready() {
			return dialogName != null && !dialogName.getValue().trim().isEmpty();
		}

		@Override
		public int getX() {
			return leftPos;
		}

		@Override
		public int getY() {
			return topPos;
		}

		@Override
		public void setX(int x) {
		}

		@Override
		public void setY(int y) {
		}

		// the dialog is added and dropped with its flag
		@Override
		public void hide() {
		}

		@Override
		public void show() {
		}

		@Override
		public boolean isHidden() {
			return false;
		}

		// the whole page, so a click off the panel cancels
		@Override
		public BoundingBox getBoundingBox() {
			return new BoundingBox(leftPos, topPos, leftPos + PAGE_WIDTH, topPos + PAGE_HEIGHT);
		}

		@Override
		public void render(GuiGraphics guiGraphics) {
			guiGraphics.fill(leftPos, topPos, leftPos + PAGE_WIDTH, topPos + PAGE_HEIGHT, DIALOG_DIM);
			int x = panelX();
			int y = panelY();
			int height = dialogHeight();
			guiGraphics.fill(x, y, x + DIALOG_WIDTH, y + height, SEARCH_EDGE);
			guiGraphics.fill(x + 1, y + 1, x + DIALOG_WIDTH - 1, y + height - 1, SEARCH_FILL);

			if (deleteConfirm) {
				renderDeleteConfirm(guiGraphics, x, y);
				return;
			}

			Component title = Component.translatable(editing()
				? "screen.capitol.book.subtab.dialog.edit_title"
				: "screen.capitol.book.subtab.dialog.title");
			guiGraphics.drawString(font, title, x + (DIALOG_WIDTH - font.width(title)) / 2, y + 6, SEARCH_INK, false);

			// the 1px ring framing the name field
			guiGraphics.fill(fieldX() - 1, fieldY() - 1, fieldX() + fieldWidth() + 1, fieldY() + BAR_HEIGHT + 1, SEARCH_EDGE);

			Component colourLabel = Component.translatable("screen.capitol.book.subtab.dialog.colour");
			guiGraphics.drawString(font, colourLabel, x + 4, y + 40, SEARCH_INK, false);

			for (int colour = 0; colour < TeamSubTab.COLOURS; colour++) {
				int chipX = swatchX(colour);
				int chipY = swatchY();
				boolean chosen = colour == dialogColour;
				int top = chosen ? chipY - CAP_LIFT : chipY;
				// the chosen colour is ringed and lifted, so it reads as the live one
				if (chosen) guiGraphics.fill(chipX - 1, top - 1, chipX + CAP_ART_W + 1, chipY + CAP_HEIGHT + 1, SEARCH_INK);
				cap(colour, CAP_ART_W).renderBlit(guiGraphics, chipX, top);
			}

			drawDialogButton(guiGraphics, x + 4, buttonY(), buttonWidth(),
				Component.translatable(editing()
					? "screen.capitol.book.subtab.dialog.save"
					: "screen.capitol.book.subtab.dialog.create"), ready());
			drawDialogButton(guiGraphics, x + 8 + buttonWidth(), buttonY(), buttonWidth(),
				Component.translatable("screen.capitol.book.subtab.dialog.cancel"), true);

			if (editing()) {
				drawDialogButton(guiGraphics, x + 4, dangerY(), DIALOG_WIDTH - 8,
					Component.translatable("screen.capitol.book.subtab.dialog.delete"), true);
			}
		}

		/** The Delete step: the panel keeps its size but asks once more before the tab is removed. */
		private void renderDeleteConfirm(GuiGraphics guiGraphics, int x, int y) {
			Component title = Component.translatable("screen.capitol.book.subtab.dialog.delete_title");
			guiGraphics.drawString(font, title, x + (DIALOG_WIDTH - font.width(title)) / 2, y + 6, SEARCH_INK, false);

			Component ask = Component.translatable("screen.capitol.book.subtab.dialog.delete_confirm",
				dialogTab == null ? "" : dialogTab.name());
			guiGraphics.drawString(font, ask, x + (DIALOG_WIDTH - font.width(ask)) / 2, y + 30, SEARCH_INK, false);

			drawDialogButton(guiGraphics, x + 4, buttonY(), buttonWidth(),
				Component.translatable("screen.capitol.book.subtab.dialog.delete"), true);
			drawDialogButton(guiGraphics, x + 8 + buttonWidth(), buttonY(), buttonWidth(),
				Component.translatable("screen.capitol.book.subtab.dialog.cancel"), true);
		}

		// a paper chip with a centred label; a chip out of season is left pale
		private void drawDialogButton(GuiGraphics guiGraphics, int x, int y, int width, Component label, boolean live) {
			guiGraphics.fill(x, y, x + width, y + BUTTON_HEIGHT, SEARCH_EDGE);
			guiGraphics.fill(x + 1, y + 1, x + width - 1, y + BUTTON_HEIGHT - 1, live ? SEARCH_FILL : 0xFFE4D9BE);
			guiGraphics.drawString(font, label, x + (width - font.width(label)) / 2,
				y + (BUTTON_HEIGHT - font.lineHeight) / 2 + 1, live ? SEARCH_INK : 0xFF8A8578, false);
		}

		private boolean inside(int x, int y, int boxX, int boxY, int width, int height) {
			return x >= boxX && x < boxX + width && y >= boxY && y < boxY + height;
		}

		@Override
		public Interactable clickStart(int x, int y) {
			int px = panelX();
			int py = panelY();
			if (x < px || x >= px + DIALOG_WIDTH || y < py || y >= py + dialogHeight()) {
				// a click off the panel is a cancel
				closeDialog();
				return this;
			}
			if (deleteConfirm) {
				if (inside(x, y, px + 4, buttonY(), buttonWidth(), BUTTON_HEIGHT)) {
					deleteSubTab();
					return this;
				}
				if (inside(x, y, px + 8 + buttonWidth(), buttonY(), buttonWidth(), BUTTON_HEIGHT)) {
					closeDialog();
					return this;
				}
				return this;
			}
			for (int colour = 0; colour < TeamSubTab.COLOURS; colour++) {
				if (x >= swatchX(colour) && x < swatchX(colour) + CAP_ART_W
					&& y >= swatchY() - CAP_LIFT && y < swatchY() + CAP_HEIGHT) {
					dialogColour = colour;
					return this;
				}
			}
			if (inside(x, y, px + 4, buttonY(), buttonWidth(), BUTTON_HEIGHT)) {
				submit();
				return this;
			}
			if (inside(x, y, px + 8 + buttonWidth(), buttonY(), buttonWidth(), BUTTON_HEIGHT)) {
				closeDialog();
				return this;
			}
			if (editing() && inside(x, y, px + 4, dangerY(), DIALOG_WIDTH - 8, BUTTON_HEIGHT)) {
				// the first press only asks; the confirmation's own Delete sends the request. The rebuild
				// takes the name field out so the confirmation reads as a panel of its own.
				deleteConfirm = true;
				rebuild();
				return this;
			}
			return this;  // on the panel but on nothing: the dialog keeps the click
		}

		/** Add sends the new tab, Save renames the edited one; the server re-checks both. */
		private void submit() {
			if (!ready()) return;
			String name = dialogName.getValue().trim();
			if (editing()) {
				PacketDistributor.sendToServer(new C2SRenameSubTab(dialogTab.id(), name, dialogColour));
			} else {
				// the server re-checks the name, the colour and the tab cap before storing any
				PacketDistributor.sendToServer(new C2SCreateSubTab(name, dialogColour));
			}
			closeDialog();
		}

		private void deleteSubTab() {
			if (dialogTab == null) return;
			PacketDistributor.sendToServer(new C2SDeleteSubTab(dialogTab.id()));
			closeDialog();
		}
	}

	// the dialog's name field, sized to the panel
	private final class DialogNameField extends TextInputInteractable {

		private DialogNameField() {
			super(SHEET.blit(BLANK_U, BLANK_V, ROW_WIDTH, BAR_HEIGHT).stretchedTo(DIALOG_WIDTH - 8, BAR_HEIGHT),
				Component.translatable("screen.capitol.book.subtab.dialog.name"), 0, 0, null, SEARCH_INK, true);
			maxLength(TeamSubTab.MAX_NAME);
		}

		@Override
		public int getX() {
			return leftPos + (PAGE_WIDTH - DIALOG_WIDTH) / 2 + 4;
		}

		@Override
		public int getY() {
			return topPos + DIALOG_TOP + 20;
		}

		@Override
		public void setX(int x) {
		}

		@Override
		public void setY(int y) {
		}
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

		// the open section is already pulled out, so it needs no tooltip
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

		// the box never moves, so hovering the ribbon or its extra 3px cannot flicker it
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
				// the ribbon keeps its top and grows the 3px downward instead
				blit.renderBlit(guiGraphics, getX(), topPos, RIBBON_WIDTH, blit.getBlitHeight() + HOVER_DROP);
			} else {
				blit.renderBlit(guiGraphics, getX(), topPos);
			}
			if (isLarge()) renderLabel(guiGraphics);
		}

		// the section's name, rotated to run bottom to top
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
			// the dialog covers the page, so a ribbon behind it is dead
			if (dialogOpen) return null;
			select(section);
			return this;
		}

		@Override
		public @Nullable Component tooltip() {
			return isLarge() || dialogOpen ? null : section.title();
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
