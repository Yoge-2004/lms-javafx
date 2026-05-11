package com.example.application.ui;

import com.example.application.ToastDisplay;
import com.example.entities.Book;
import com.example.entities.BorrowRequest;
import com.example.entities.User;
import com.example.entities.BooksDB;
import com.example.entities.BooksDB.IssueRecord;
import com.example.services.BookService;
import com.example.services.ReminderService;
import com.example.services.UserService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.control.cell.PropertyValueFactory;
import java.time.LocalDateTime;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import com.example.services.InvoiceService;

/**
 * Circulation view — issues, returns, renewals, and borrow-request approval.
 * <h1>CirculationView</h1>
 *
 * <p>The central hub for managing the lifecycle of book loans, borrow requests, 
 * and fine settlements. This view provides a high-density, multi-tab interface
 * designed for librarians to efficiently process transactions while maintaining
 * visibility into library health.</p>
 *
 * <h3>Core Functionality:</h3>
 * <ul>
 *   <li><b>Loan Management:</b> Tracking active issues, processing returns, and
 *       managing loan extensions (renewals).</li>
 *   <li><b>Request Handling:</b> A workflow-driven system for approving or
 *       rejecting book borrow requests from users.</li>
 *   <li><b>Overdue Monitoring:</b> Real-time tracking of overdue items with 
 *       integrated email reminder capabilities.</li>
 *   <li><b>Fine Settlement:</b> Management of outstanding fines and processing
 *       of member payments.</li>
 *   <li><b>Circulation History:</b> Comprehensive audit trail of all past transactions.</li>
 * </ul>
 *
 * <h3>Design Features:</h3>
 * <ul>
 *   <li><b>Vibrant Visual Language:</b> Utilizes a diversified color palette for
 *       actions (e.g., Violet for Returns, Indigo for Renewals) to reduce cognitive
 *       load and improve operational speed.</li>
 *   <li><b>Responsive Tables:</b> Uses constrained resize policies and dynamic
 *       cell factories for a fluid, high-fidelity data presentation.</li>
 *   <li><b>Premium Interactions:</b> Integrated staggered entrance animations and
 *       hover effects via {@link AppTheme}.</li>
 * </ul>
 *
 * @author Yogesh
 * @version 4.0
 */
public class CirculationView extends BorderPane {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm");

    private final ObservableList<IssueRecord> issueRecords;
    private final boolean isStaff;
    private final String currentUser;
    private final Runnable onRefresh;
    private final ToastDisplay toast;
    private final Runnable refreshAllData;
    private final ObservableList<BooksDB.InvoiceData> historyList;

    private TextField searchField;
    private javafx.collections.transformation.FilteredList<IssueRecord> filteredIssues;
    private javafx.collections.transformation.FilteredList<BorrowRequest> filteredRequests;
    private javafx.collections.transformation.FilteredList<IssueRecord> filteredOverdue;
    private javafx.collections.transformation.FilteredList<IssueRecord> filteredUserOverdue;
    private javafx.collections.transformation.FilteredList<IssueRecord> filteredSettlements;
    private javafx.collections.transformation.FilteredList<BooksDB.InvoiceData> filteredHistory;

    private TableView<IssueRecord>  issuesTable;
    private TableView<BorrowRequest> requestsTable;
    private TableView<IssueRecord>  overdueTable;
    private TableView<IssueRecord>  userOverdueTable;
    private TableView<IssueRecord>  settlementsTable;
    private TableView<BooksDB.InvoiceData> historyTable;

    private javafx.collections.transformation.SortedList<BorrowRequest> sortedRequests;
    private static int lastSelectedTabIndex = 0;

    private Button remindAllBtn;


    /**
     * Constructs a new CirculationView with multi-tab management.
     *
     * @param issueRecords Observable list of current active and historical loans.
     * @param borrowRequests Observable list of user borrow requests.
     * @param historyList Observable list of past payment/invoice records.
     * @param isStaff Whether the current user has staff/librarian permissions.
     * @param currentUser The ID of the currently logged-in user.
     * @param onRefresh Callback to refresh high-level state.
     * @param toast Reference to the global notification system.
     * @param refreshAllData Global data refresh callback.
     */
    public CirculationView(ObservableList<IssueRecord> issueRecords,
                           ObservableList<BorrowRequest> borrowRequests,
                           ObservableList<BooksDB.InvoiceData> historyList,
                           boolean isStaff, String currentUser,
                           Runnable onRefresh, ToastDisplay toast, Runnable refreshAllData) {
        this.issueRecords  = issueRecords;
        this.isStaff       = isStaff;
        this.currentUser   = currentUser;
        this.onRefresh     = onRefresh;
        this.toast         = toast;
        this.refreshAllData = refreshAllData;
        this.historyList   = historyList;

        this.filteredIssues = new javafx.collections.transformation.FilteredList<>(issueRecords,
                r -> matchesActiveIssue(r, ""));

        this.filteredRequests = new javafx.collections.transformation.FilteredList<>(borrowRequests,
                r -> matchesRequest(r, ""));

        this.filteredOverdue = new javafx.collections.transformation.FilteredList<>(issueRecords,
                r -> matchesOverdue(r, ""));

        this.filteredUserOverdue = new javafx.collections.transformation.FilteredList<>(issueRecords,
                r -> !isStaff && matchesOverdue(r, ""));

        // Filter: Past loans that were returned with outstanding fines
        this.filteredSettlements = new javafx.collections.transformation.FilteredList<>(issueRecords,
                r -> matchesSettlement(r, ""));

        // Sort: Prioritize pending borrow requests for librarian review
        this.sortedRequests = new javafx.collections.transformation.SortedList<>(filteredRequests);
        this.sortedRequests.setComparator((r1, r2) -> {
            if (r1.getStatus() != r2.getStatus()) {
                if (r1.getStatus() == BorrowRequest.Status.PENDING) return -1;
                if (r2.getStatus() == BorrowRequest.Status.PENDING) return 1;
            }
            return r2.getRequestedAt().compareTo(r1.getRequestedAt());
        });

        this.filteredHistory = new javafx.collections.transformation.FilteredList<>(historyList, h -> true);

        initUI();
        initFiltering();
        bind();

        TabPane tabPane = findTabPane(this);
        if (tabPane != null) {
            tabPane.getSelectionModel().select(Math.min(lastSelectedTabIndex, tabPane.getTabs().size() - 1));
            tabPane.getSelectionModel().selectedIndexProperty().addListener((obs, old, val) ->
                    lastSelectedTabIndex = val.intValue());
        }
    }


    /** Attaches real-time search listeners to all categorized filtered lists. */
    private void initFiltering() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String q = newVal == null ? "" : newVal.trim().toLowerCase();
            filteredIssues.setPredicate(r -> matchesActiveIssue(r, q));
            filteredRequests.setPredicate(r -> matchesRequest(r, q));
            filteredOverdue.setPredicate(r -> matchesOverdue(r, q));
            filteredUserOverdue.setPredicate(r -> !isStaff && matchesOverdue(r, q));
            filteredSettlements.setPredicate(r -> matchesSettlement(r, q));
            filteredHistory.setPredicate(h -> {
                return q.isEmpty() || h.getId().toLowerCase().contains(q) || h.getUserId().toLowerCase().contains(q);
            });
        });
    }

    private boolean matchesActiveIssue(IssueRecord record, String query) {
        if (record == null || record.isReturned()) return false;
        if (!isStaff && !currentUser.equals(record.getUserId())) return false;
        return matchesIssueQuery(record, query);
    }

    private boolean matchesOverdue(IssueRecord record, String query) {
        if (record == null || !record.isOverdue() || record.getRemainingFine() <= 0) return false;
        if (!isStaff && !currentUser.equals(record.getUserId())) return false;
        return matchesIssueQuery(record, query);
    }

    private boolean matchesSettlement(IssueRecord record, String query) {
        if (record == null || !record.isReturned() || record.getRemainingFine() <= 0) return false;
        return matchesIssueQuery(record, query);
    }

    private boolean matchesIssueQuery(IssueRecord record, String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        return q.isEmpty()
                || safeLower(record.getBookTitle()).contains(q)
                || safeLower(record.getUserId()).contains(q)
                || safeLower(record.getIsbn()).contains(q);
    }

    private boolean matchesRequest(BorrowRequest request, String query) {
        if (request == null) return false;
        if (!isStaff && !currentUser.equals(request.getUserId())) return false;
        String q = query == null ? "" : query.trim().toLowerCase();
        return q.isEmpty()
                || safeLower(request.getBookTitle()).contains(q)
                || safeLower(request.getUserId()).contains(q)
                || safeLower(request.getIsbn()).contains(q);
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }


    // ═══════════════════════════════════════════════════════════════
    // Initialization
    // ═══════════════════════════════════════════════════════════════

    /** Sets up the primary visual container and unified search bar. */
    private void initUI() {
        setStyle("-fx-background-color: " + pageBackground() + ";");

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color:" + pageBackground() + ";");

        searchField = new TextField();
        searchField.setPromptText("Search by title or user...");
        searchField.setStyle(inputStyle());
        searchField.setPrefWidth(300);
        searchField.setMaxWidth(300);

        HBox searchRow = new HBox(searchField);
        searchRow.setAlignment(Pos.CENTER_RIGHT);

        content.getChildren().addAll(buildHeader(), searchRow, buildTabs());
        scroll.setContent(content);
        setCenter(scroll);
    }

    private VBox buildHeader() {
        HBox titleRow = new HBox(12);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        StackPane badge = new StackPane(AppTheme.createIcon(AppTheme.ICON_SYNC, 18));
        badge.setMinSize(40, 40);
        badge.setPrefSize(40, 40);
        badge.setStyle("-fx-background-color:#0D948822; -fx-background-radius:12px;");

        VBox textBlock = new VBox(4);
        Label title = new Label("Circulation");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Manage book issues, returns, renewals and borrow requests");
        sub.getStyleClass().add("page-subtitle");
        textBlock.getChildren().addAll(title, sub);

        titleRow.getChildren().addAll(badge, textBlock);

        VBox h = new VBox(titleRow);
        return h;
    }

    private TabPane buildTabs() {
        TabPane tp = new TabPane();
        tp.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tp.setStyle("-fx-background-color:transparent;");

        tp.getTabs().add(tab("Active Issues", AppTheme.ICON_LIBRARY, issuesPanel()));
        tp.getTabs().add(tab("Borrow Requests", AppTheme.ICON_NOTIFICATION, requestsPanel()));
        if (isStaff) {
            tp.getTabs().add(tab("Overdue", AppTheme.ICON_WARNING, overduePanel()));
            tp.getTabs().add(tab("Settlements", AppTheme.ICON_SYNC, settlementsPanel()));
            tp.getTabs().add(tab("Payment History", AppTheme.ICON_CARD, historyPanel()));
        } else {
            tp.getTabs().add(tab("My Overdue", AppTheme.ICON_WARNING, userOverduePanel()));
            tp.getTabs().add(tab("My Fines", AppTheme.ICON_SYNC, userSettlementsPanel()));
            tp.getTabs().add(tab("My Receipts", AppTheme.ICON_CARD, userHistoryPanel()));
        }

        return tp;
    }

    // ═══════════════════════════════════════════════════════════════
    // Active Issues Panel
    // ═══════════════════════════════════════════════════════════════

    /** Constructs the layout for the primary Active Issues tab. */
    private VBox issuesPanel() {
        VBox p = new VBox(12);
        p.setFillWidth(true);

        if (isStaff) {
            Button issueBtn = AppTheme.createIconTextButton(
                    "Issue Book", AppTheme.ICON_ADD, AppTheme.ButtonStyle.PRIMARY);
            issueBtn.setOnAction(e -> showIssueDialog());

            HBox bar = new HBox(issueBtn);
            bar.setAlignment(Pos.CENTER_LEFT);
            bar.setPadding(new Insets(0, 0, 0, 4)); // Shift 4px right to prevent clipping on hover
            p.getChildren().add(bar);
        }

        issuesTable = buildIssuesTable();
        issuesTable.setFixedCellSize(48.0); // Unified row height for consistent scrolling
        issuesTable.setItems(filteredIssues);
        VBox.setVgrow(issuesTable, Priority.ALWAYS);
        p.getChildren().add(issuesTable);
        return p;
    }

    /**
     * Configures the main issues table with status chips and conditional actions.
     * Handles both staff (full actions) and regular user (return/renew only) views.
     */
    private TableView<IssueRecord> buildIssuesTable() {
        TableView<IssueRecord> t = new TableView<>();
        t.getStyleClass().add("table-view");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setPlaceholder(new Label("No active issues"));

        TableColumn<IssueRecord, String> titleC = col("Book Title",
                r -> r.getBookTitle(), 200);
        TableColumn<IssueRecord, String> userC  = col("Borrower",
                r -> r.getUserId(), 110);
        TableColumn<IssueRecord, String> issueC = col("Issued",
                r -> r.getIssueDate().format(DATE_FMT), 100);
        TableColumn<IssueRecord, String> dueC   = col("Due Date",
                r -> r.getDueDate().format(DATE_FMT), 100);
        TableColumn<IssueRecord, String> qtyC   = colC("Qty",
                r -> String.valueOf(r.getQuantity()), 50);

        // Status chip
        TableColumn<IssueRecord, Void> statusC = new TableColumn<>("Status");
        statusC.setSortable(false);
        statusC.getStyleClass().add("col-center");
        statusC.setPrefWidth(125); // Increased for better spacing
        statusC.setCellFactory(c -> new TableCell<>() {
            { getStyleClass().add("col-center"); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null)
                { setGraphic(null); return; }
                IssueRecord r = getTableRow().getItem();
                Label chip = new Label(r.getStatusText());
                chip.getStyleClass().addAll("chip", r.getStatusStyleClass());
                setGraphic(chip);
            }
        });


        // Actions
        TableColumn<IssueRecord, Void> actC = new TableColumn<>("Actions");
        actC.setSortable(false);
        actC.getStyleClass().add("col-center");
        actC.setPrefWidth(120);
        actC.setCellFactory(c -> new TableCell<>() {
            { getStyleClass().add("col-center"); }
            final Button retBtn  = actionIconBtn(AppTheme.ICON_RETURN, "Return book", "#8B5CF6", AppTheme.ButtonStyle.VIOLET);
            final Button renBtn  = actionIconBtn(AppTheme.ICON_REFRESH,  "Renew loan", "#6366F1", AppTheme.ButtonStyle.INDIGO);
            {
                retBtn.setOnAction(e -> returnBook(getRow()));
                renBtn.setOnAction(e -> renewBook(getRow()));
            }
            private IssueRecord getRow() {
                return getTableView().getItems().get(getIndex());
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null)
                { setGraphic(null); return; }
                IssueRecord r = getTableRow().getItem();
                HBox box = new HBox(4);
                box.setAlignment(Pos.CENTER);
                if (isStaff || currentUser.equals(r.getUserId()))
                    box.getChildren().add(retBtn);
                if (r.canRenew()) box.getChildren().add(renBtn);
                setGraphic(box);
            }
        });

        if (isStaff) {
            t.getColumns().add(titleC);
            t.getColumns().add(userC);
            t.getColumns().add(issueC);
            t.getColumns().add(dueC);
            t.getColumns().add(qtyC);
            t.getColumns().add(statusC);
            t.getColumns().add(actC);
        } else {
            t.getColumns().add(titleC);
            t.getColumns().add(issueC);
            t.getColumns().add(dueC);
            t.getColumns().add(qtyC);
            t.getColumns().add(statusC);
            t.getColumns().add(actC);
        }
        return t;
    }

    // ═══════════════════════════════════════════════════════════════
    // Borrow Requests Panel
    // ═══════════════════════════════════════════════════════════════

    /** Constructs the layout for managing user borrow requests. */
    private VBox requestsPanel() {
        VBox p = new VBox(12);
        p.setFillWidth(true);

        requestsTable = buildRequestsTable();
        requestsTable.setFixedCellSize(48.0); // Unified row height for consistent scrolling
        requestsTable.setItems(sortedRequests);
        VBox.setVgrow(requestsTable, Priority.ALWAYS);
        p.getChildren().add(requestsTable);
        return p;
    }


    /**
     * Configures the requests table with status chips and administrative controls.
     * Includes tooltips for full timestamp visibility.
     */
    private TableView<BorrowRequest> buildRequestsTable() {
        TableView<BorrowRequest> t = new TableView<>();
        t.getStyleClass().add("table-view");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setPlaceholder(new Label("No borrow requests"));

        TableColumn<BorrowRequest, String> titleC = col2("Book Title",
                r -> r.getBookTitle(), 180);
        TableColumn<BorrowRequest, String> userC  = col2("Requested By",
                r -> r.getUserId(), 110);
        TableColumn<BorrowRequest, String> qtyC   = col2C("Qty",
                r -> String.valueOf(r.getQuantity()), 50);
        TableColumn<BorrowRequest, String> dateC = new TableColumn<>("Requested");
        dateC.setSortable(true);
        dateC.setPrefWidth(110);
        dateC.getStyleClass().add("col-left");
        dateC.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getRequestedAt().format(DATETIME_FMT)));
        dateC.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); AppTheme.installSmartTooltip(this, ""); return; }
                setText(s);
                BorrowRequest req = getTableRow() != null ? getTableRow().getItem() : null;
                if (req != null) {
                    String full = req.getRequestedAt().format(
                            DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy 'at' HH:mm:ss"));
                    AppTheme.installSmartTooltip(this, full);
                }
            }
        });

        // Status chip
        TableColumn<BorrowRequest, Void> statusC = new TableColumn<>("Status");
        statusC.setSortable(false);
        statusC.getStyleClass().add("col-center");
        statusC.setPrefWidth(120);
        statusC.setCellFactory(c -> new TableCell<>() {
            { getStyleClass().add("col-center"); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null)
                { setGraphic(null); return; }
                BorrowRequest req = getTableRow().getItem();
                Label chip = new Label(req.getStatus().name());
                String sc = switch (req.getStatus()) {
                    case PENDING  -> "chip-warning";
                    case APPROVED -> "chip-success";
                    case REJECTED -> "chip-error";
                };
                chip.getStyleClass().addAll("chip", sc);
                setGraphic(chip);
            }
        });

        // Note/rejection reason column — only shows for REJECTED requests
        TableColumn<BorrowRequest, String> noteC = makeCol("Reason",
                r -> r.getStatus() == BorrowRequest.Status.REJECTED && r.getNote() != null ? r.getNote() : "", 220);
        noteC.setCellFactory(col -> new TableCell<>() {
            // FIX #11: Use a standard JavaFX Tooltip attached once to the cell, not
            // installSmartTooltip which adds new Popup event handlers on every updateItem
            // call (table cells are recycled, causing multiple competing popups = flashing).
            private final Tooltip cellTooltip = new Tooltip();
            {
                cellTooltip.getStyleClass().add("modern-tooltip");
                cellTooltip.setWrapText(true);
                cellTooltip.setMaxWidth(320);
                cellTooltip.setShowDelay(javafx.util.Duration.millis(250));
                cellTooltip.setHideDelay(javafx.util.Duration.millis(200));
                Tooltip.install(this, cellTooltip);
            }
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null || s.isEmpty()) {
                    setText(null); setGraphic(null);
                    cellTooltip.setText("");
                    setTooltip(null);
                    return;
                }
                String display = s.length() > 72 ? s.substring(0, 69) + "..." : s;
                setText(display);
                setStyle("-fx-text-fill:#DC2626; -fx-font-size:12px;");
                setAlignment(Pos.CENTER_LEFT);
                cellTooltip.setText(s);
                setTooltip(cellTooltip);
            }
        });

        t.getColumns().add(titleC);
        t.getColumns().add(userC);
        t.getColumns().add(qtyC);
        t.getColumns().add(dateC);
        t.getColumns().add(statusC);
        t.getColumns().add(noteC);

        if (isStaff) {
            TableColumn<BorrowRequest, Void> actC = new TableColumn<>();
            Label actH = new Label("Actions");
            actH.setStyle("-fx-padding:0; -fx-alignment:CENTER;");
            actH.setMinWidth(120);
            actC.setGraphic(actH);
            actC.setSortable(false);
            actC.getStyleClass().add("col-center");
            actC.setPrefWidth(120);
            actC.setCellFactory(c -> new TableCell<>() {
                { getStyleClass().add("col-center"); }
                final Button appr = actionIconBtn(AppTheme.ICON_CHECK, "Approve request", "#16A34A", AppTheme.ButtonStyle.SUCCESS);
                final Button rej  = actionIconBtn(AppTheme.ICON_CLOSE, "Reject request", "#DC2626", AppTheme.ButtonStyle.DANGER);
                final Button view = actionIconBtn(AppTheme.ICON_VISIBILITY, "View reason", "#6366F1", AppTheme.ButtonStyle.INDIGO);
                {
                    appr.setOnAction(e -> approveRequest(getTableView().getItems().get(getIndex())));
                    rej .setOnAction(e -> rejectRequest (getTableView().getItems().get(getIndex())));
                    view.setOnAction(e -> {
                        BorrowRequest req = getTableView().getItems().get(getIndex());
                        showLongTextDialog("Request Reason", req.getNote());
                    });
                }
                @Override protected void updateItem(Void v, boolean empty) {
                    super.updateItem(v, empty);
                    if (empty || getTableRow() == null || getTableRow().getItem() == null)
                    { setGraphic(null); return; }
                    BorrowRequest req = getTableRow().getItem();

                    HBox box = new HBox(6);
                    box.setAlignment(Pos.CENTER);
                    if (req.isPending()) box.getChildren().addAll(appr, rej);
                    // FIX #11: only show the eye icon for REJECTED requests that have a note
                    if (req.getStatus() == BorrowRequest.Status.REJECTED
                            && req.getNote() != null && !req.getNote().isEmpty())
                        box.getChildren().add(view);

                    setGraphic(box);
                }
            });
            t.getColumns().add(actC);
        }
        return t;
    }

    // ═══════════════════════════════════════════════════════════════
    // Overdue Management
    // ═══════════════════════════════════════════════════════════════

    /** Constructs the specialized panel for tracking and resolving overdue loans. */
    private VBox overduePanel() {
        VBox p = new VBox(12);
        p.setFillWidth(true);

        HBox banner = new HBox(12);
        banner.setStyle("-fx-background-color:" + overdueBannerBackground() + "; -fx-background-radius:12px; " +
                "-fx-border-radius:12px; -fx-border-color:" + overdueBannerBorder() + "; -fx-border-width:1;");
        banner.setPadding(new Insets(16));
        banner.setAlignment(Pos.CENTER_LEFT);
        StackPane icon = new StackPane(AppTheme.createIcon(AppTheme.ICON_WARNING, 18));
        icon.setMinSize(40, 40);
        icon.setPrefSize(40, 40);
        icon.setStyle("-fx-background-color:" + overdueIconSurface() + "; -fx-background-radius:12px;");
        VBox txt = new VBox(2,
                styledLabel("Overdue Books Alert", 16, overdueBannerTitle(), true),
                styledLabel("These records have exceeded their due date.", 13, overdueBannerText(), false));

        remindAllBtn = AppTheme.createIconTextButton("Remind All Overdue", AppTheme.ICON_MAIL, AppTheme.ButtonStyle.PRIMARY);
        remindAllBtn.setOnAction(e -> bulkRemindOverdue());
        updateRemindAllBtnState();

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        banner.getChildren().addAll(icon, txt, sp, remindAllBtn);

        p.getChildren().add(banner);

        buildOverdueTable(p, banner);
        return p;
    }

    private VBox settlementsPanel() {
        VBox p = new VBox(16);
        p.setFillWidth(true);

        // ── Pending User Payment Approvals ────────────────────────────────────
        Label approvalHdr = new Label("Pending Payment Approvals from Members");
        approvalHdr.setStyle("-fx-font-weight:700; -fx-font-size:13px;");

        javafx.collections.ObservableList<com.example.entities.PaymentApprovalRequest> approvalItems =
                javafx.collections.FXCollections.observableArrayList(
                        com.example.services.PaymentApprovalService.getPendingRequests());

        TableView<com.example.entities.PaymentApprovalRequest> approvalTable = new TableView<>(approvalItems);
        approvalTable.setFixedCellSize(44.0);
        approvalTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        approvalTable.setMaxHeight(220);
        approvalTable.setPlaceholder(new Label("No pending payment requests."));
        approvalTable.getStyleClass().add("table-view");

        TableColumn<com.example.entities.PaymentApprovalRequest, String> paUser = new TableColumn<>("Member");
        paUser.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().getUserId()));
        TableColumn<com.example.entities.PaymentApprovalRequest, String> paBook = new TableColumn<>("Book");
        paBook.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().getBookTitle()));
        TableColumn<com.example.entities.PaymentApprovalRequest, String> paAmt = new TableColumn<>("Amount");
        paAmt.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                AppTheme.formatCurrency(cd.getValue().getAmount()) + " / " +
                        AppTheme.formatCurrency(cd.getValue().getTotalFineAtRequest())));

        TableColumn<com.example.entities.PaymentApprovalRequest, Void> paAct = new TableColumn<>("Actions");
        paAct.setCellFactory(col -> new TableCell<>() {
            final Button approveBtn = AppTheme.createIconTextButton("Approve", AppTheme.ICON_CHECK, AppTheme.ButtonStyle.PRIMARY);
            final Button rejectBtn  = AppTheme.createIconTextButton("Reject",  AppTheme.ICON_CANCEL, AppTheme.ButtonStyle.DANGER);
            final HBox box = new HBox(6, approveBtn, rejectBtn);
            { box.setAlignment(javafx.geometry.Pos.CENTER);
                approveBtn.setOnAction(e -> {
                    com.example.entities.PaymentApprovalRequest r = getTableRow().getItem();
                    if (r == null) return;
                    com.example.services.PaymentApprovalService.approveRequest(r.getRequestId(), currentUser, toast);
                    approvalItems.setAll(com.example.services.PaymentApprovalService.getPendingRequests());
                    if (onRefresh != null) onRefresh.run();
                });
                rejectBtn.setOnAction(e -> {
                    com.example.entities.PaymentApprovalRequest r = getTableRow().getItem();
                    if (r == null) return;
                    javafx.scene.control.TextInputDialog td = new javafx.scene.control.TextInputDialog();
                    td.setTitle("Reject Payment"); td.setHeaderText("Reason for rejection (optional):");
                    td.showAndWait().ifPresent(reason -> {
                        com.example.services.PaymentApprovalService.rejectRequest(r.getRequestId(), currentUser, reason, toast);
                        approvalItems.setAll(com.example.services.PaymentApprovalService.getPendingRequests());
                    });
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty || getTableRow() == null || getTableRow().getItem() == null ? null : box);
            }
        });
        approvalTable.getColumns().addAll(
                java.util.List.of(paUser, paBook, paAmt, paAct));

        // ── Returned-Book Settlement Fines ────────────────────────────────────
        Label settlementsHdr = new Label("Outstanding Fines on Returned Books");
        settlementsHdr.setStyle("-fx-font-weight:700; -fx-font-size:13px;");

        settlementsTable = buildSettlementsTable();
        settlementsTable.setFixedCellSize(44.0);
        settlementsTable.setItems(filteredSettlements);
        VBox.setVgrow(settlementsTable, Priority.ALWAYS);

        p.getChildren().addAll(approvalHdr, approvalTable, settlementsHdr, settlementsTable);
        return p;
    }

    private VBox userOverduePanel() {
        VBox p = new VBox(12);
        p.setFillWidth(true);

        HBox banner = new HBox(12);
        banner.setPadding(new Insets(12));
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setStyle("-fx-background-color:" + overdueBannerBackground() + "; -fx-background-radius:10px; " +
                "-fx-border-color:" + overdueBannerBorder() + "; -fx-border-width:1; -fx-border-radius:10px;");
        StackPane icon = new StackPane(AppTheme.createIcon(AppTheme.ICON_WARNING, 16));
        icon.setMinSize(34, 34);
        icon.setPrefSize(34, 34);
        icon.setStyle("-fx-background-color:" + overdueIconSurface() + "; -fx-background-radius:10px;");
        Label bannerText = new Label("Books past their due date are listed here with the live fine currently due.");
        bannerText.setStyle("-fx-font-size:12px; -fx-text-fill:" + overdueBannerText() + ";");
        bannerText.setWrapText(true);
        banner.getChildren().addAll(icon, bannerText);

        userOverdueTable = new TableView<>();
        userOverdueTable.getStyleClass().add("table-view");
        userOverdueTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        userOverdueTable.setFixedCellSize(44.0);
        userOverdueTable.setPlaceholder(new Label("No overdue books."));

        userOverdueTable.getColumns().add(colIR("Book Title", r -> r.getBookTitle(), 200));
        userOverdueTable.getColumns().add(colIR("Due Date", r -> r.getDueDate().format(DATE_FMT), 110));
        userOverdueTable.getColumns().add(colIRC("Days Overdue", r -> String.valueOf(r.getDaysOverdue()), 100));
        userOverdueTable.getColumns().add(colIRC("Fine Due", r -> AppTheme.formatCurrency(r.getRemainingFine()), 110));

        TableColumn<IssueRecord, Void> actC = new TableColumn<>("Actions");
        actC.getStyleClass().add("col-center");
        actC.setPrefWidth(110);
        actC.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("col-center"); }
            final Button returnBtn = actionIconBtn(AppTheme.ICON_RETURN, "Return overdue book", "#8B5CF6", AppTheme.ButtonStyle.VIOLET);
            {
                returnBtn.setOnAction(e -> {
                    IssueRecord record = getTableRow() != null ? getTableRow().getItem() : null;
                    if (record != null) returnBook(record);
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty || getTableRow() == null || getTableRow().getItem() == null ? null : returnBtn);
            }
        });
        userOverdueTable.getColumns().add(actC);
        userOverdueTable.setItems(filteredUserOverdue);
        VBox.setVgrow(userOverdueTable, Priority.ALWAYS);

        p.getChildren().addAll(banner, userOverdueTable);
        return p;
    }

    /**
     * Constructs a simplified settlements panel for regular users showing only
     * their own outstanding fines. Users can pay here after choosing "Pay Later"
     * during book return.
     * FIX: Was previously missing — users had no way to see/pay deferred fines.
     */
    private VBox userSettlementsPanel() {
        VBox p = new VBox(12);
        p.setFillWidth(true);

        // Info banner
        HBox banner = new HBox(12);
        banner.setPadding(new Insets(12));
        banner.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        banner.setStyle("-fx-background-color:" + (AppTheme.darkMode ? "rgba(99,102,241,0.18)" : "#EEF2FF") +
                "; -fx-background-radius:10px; -fx-border-color:" + (AppTheme.darkMode ? "#4F46E5" : "#C7D2FE") +
                "; -fx-border-width:1; -fx-border-radius:10px;");
        Label bannerText = new Label("Outstanding fines from returned books. You can pay them here at any time.");
        bannerText.setStyle("-fx-font-size:12px; -fx-text-fill:" + (AppTheme.darkMode ? "#C7D2FE" : "#3730A3") + ";");
        bannerText.setWrapText(true);
        banner.getChildren().add(bannerText);
        p.getChildren().add(banner);

        // Show status of any pending/processed payment requests so user isn't left wondering
        List<com.example.entities.PaymentApprovalRequest> myRequests =
                com.example.services.PaymentApprovalService.getRequestsForUser(currentUser);
        if (!myRequests.isEmpty()) {
            VBox requestsBox = new VBox(4);
            requestsBox.setStyle("-fx-background-color:" + (AppTheme.darkMode ? "rgba(30,30,30,0.6)" : "#F8FAFC") +
                    "; -fx-background-radius:8; -fx-padding:10;");
            Label requestsHdrLbl = new Label("My Payment Requests");
            requestsHdrLbl.setStyle("-fx-font-weight:700; -fx-font-size:12px;");
            requestsBox.getChildren().add(requestsHdrLbl);
            for (com.example.entities.PaymentApprovalRequest req : myRequests) {
                String statusColor = switch (req.getStatus()) {
                    case PENDING  -> AppTheme.darkMode ? "#FCD34D" : "#B45309";
                    case APPROVED -> AppTheme.darkMode ? "#4ADE80" : "#15803D";
                    case REJECTED -> AppTheme.darkMode ? "#F87171" : "#B91C1C";
                };
                String statusText = switch (req.getStatus()) {
                    case PENDING  -> "⏳ Awaiting approval";
                    case APPROVED -> "✅ Approved by " + req.getProcessedBy();
                    case REJECTED -> "❌ Rejected: " + (req.getNote() != null ? req.getNote() : "");
                };
                Label reqLbl = new Label(req.getBookTitle() + " — " +
                        AppTheme.formatCurrency(req.getAmount()) + "  |  " + statusText);
                reqLbl.setStyle("-fx-font-size:11px; -fx-text-fill:" + statusColor + ";");
                reqLbl.setWrapText(true);
                requestsBox.getChildren().add(reqLbl);
            }
            p.getChildren().add(requestsBox);
        }

        // User-scoped filter: only show this user's settled-but-unpaid fines
        javafx.collections.transformation.FilteredList<IssueRecord> mySettlements =
                new javafx.collections.transformation.FilteredList<>(issueRecords,
                        r -> r.isReturned() && r.getRemainingFine() > 0 && currentUser.equals(r.getUserId()));

        TableView<IssueRecord> t = new TableView<>();
        t.getStyleClass().add("table-view");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setFixedCellSize(44.0);
        t.setPlaceholder(new Label("No outstanding fines! 🎉"));

        t.getColumns().add(colIR("Book Title",  r -> r.getBookTitle(), 200));
        t.getColumns().add(colIR("Return Date", r -> r.getReturnDate() != null ? r.getReturnDate().format(DATE_FMT) : "-", 110));
        t.getColumns().add(colIRC("Fine Due",   r -> AppTheme.formatCurrency(r.getRemainingFine()), 110));

        TableColumn<IssueRecord, Void> actC = new TableColumn<>("Actions");
        actC.getStyleClass().add("col-center");
        actC.setPrefWidth(90);
        actC.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("col-center"); }
            // Compact icon-only pay button matching the Settlements tab style
            final Button payBtn = actionIconBtn(AppTheme.ICON_CARD, "Pay Fine", "#3B82F6", AppTheme.ButtonStyle.PRIMARY);
            {
                payBtn.setOnAction(e -> {
                    IssueRecord r = getTableRow().getItem();
                    if (r != null) processFinePayment(r, r.getRemainingFine());
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(payBtn);
                }
            }
        });
        t.getColumns().add(actC);
        t.setItems(mySettlements);
        VBox.setVgrow(t, Priority.ALWAYS);
        p.getChildren().add(t);
        return p;
    }

    private VBox historyPanel() {
        VBox p = new VBox(12);
        p.setFillWidth(true);

        historyTable = buildHistoryTable();
        historyTable.setFixedCellSize(44.0);
        historyTable.setItems(filteredHistory);
        VBox.setVgrow(historyTable, Priority.ALWAYS);
        p.getChildren().add(historyTable);
        return p;
    }

    /** User-scoped receipt history — shows only the current user's payment invoices. */
    private VBox userHistoryPanel() {
        VBox p = new VBox(12);
        p.setFillWidth(true);

        // Info banner
        HBox banner = new HBox(12);
        banner.setPadding(new Insets(12));
        banner.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        banner.setStyle("-fx-background-color:" + (AppTheme.darkMode ? "rgba(14,165,233,0.18)" : "#E0F2FE") +
                "; -fx-background-radius:10px; -fx-border-color:" + (AppTheme.darkMode ? "#0EA5E9" : "#BAE6FD") +
                "; -fx-border-width:1; -fx-border-radius:10px;");
        Label bannerText = new Label("Your payment receipts. Each entry was generated when a fine was paid.");
        bannerText.setStyle("-fx-font-size:12px; -fx-text-fill:" + (AppTheme.darkMode ? "#BAE6FD" : "#0369A1") + ";");
        bannerText.setWrapText(true);
        banner.getChildren().add(bannerText);
        p.getChildren().add(banner);

        // Filter the shared historyList to only this user's invoices
        javafx.collections.transformation.FilteredList<BooksDB.InvoiceData> myHistory =
                new javafx.collections.transformation.FilteredList<>(historyList,
                        h -> currentUser.equals(h.getUserId()));

        TableView<BooksDB.InvoiceData> t = new TableView<>();
        t.getStyleClass().add("table-view");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setFixedCellSize(44.0);
        t.setPlaceholder(new Label("No payment receipts yet."));

        TableColumn<BooksDB.InvoiceData, String> idCol = new TableColumn<>("Invoice ID");
        idCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().getId()));
        idCol.setPrefWidth(120);
        idCol.getStyleClass().add("col-center");
        idCol.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("col-center"); }
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : v);
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        });

        TableColumn<BooksDB.InvoiceData, String> bookCol = new TableColumn<>("Book");
        bookCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().getBookTitle()));
        bookCol.setPrefWidth(200);
        bookCol.getStyleClass().add("col-center");
        bookCol.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("col-center"); }
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : v);
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        });

        TableColumn<BooksDB.InvoiceData, Double> amtCol = new TableColumn<>("Amount Paid");
        amtCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().getAmount()));
        amtCol.getStyleClass().add("col-center");
        amtCol.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("col-center"); }
            @Override protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : AppTheme.formatCurrency(v));
                setAlignment(javafx.geometry.Pos.CENTER);
                setStyle(empty ? "" : "-fx-font-weight:700; -fx-text-fill:#16A34A;");
            }
        });
        amtCol.setPrefWidth(110);

        TableColumn<BooksDB.InvoiceData, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(cd -> {
            java.time.LocalDateTime d = cd.getValue().getDate();
            return new javafx.beans.property.SimpleStringProperty(
                    d != null ? d.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "-");
        });
        dateCol.setPrefWidth(140);
        dateCol.getStyleClass().add("col-center");
        dateCol.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("col-center"); }
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : v);
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        });

        // FIX #24: Print action column — lets the user print their receipt
        TableColumn<BooksDB.InvoiceData, Void> printC = new TableColumn<>("Print");
        printC.getStyleClass().add("col-center");
        printC.setPrefWidth(72);
        printC.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("col-center"); }
            final Button btn = actionIconBtn(AppTheme.ICON_PRINT, "Print invoice", "#6366F1", AppTheme.ButtonStyle.INDIGO);
            {
                btn.setOnAction(ev -> {
                    BooksDB.InvoiceData data = getTableRow() != null ? getTableRow().getItem() : null;
                    if (data == null) return;
                    printUserInvoice(data);
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty || getTableRow() == null || getTableRow().getItem() == null ? null : btn);
            }
        });

        t.getColumns().addAll(
                java.util.List.of(idCol, bookCol, amtCol, dateCol, printC));
        t.setItems(myHistory);
        VBox.setVgrow(t, Priority.ALWAYS);
        p.getChildren().add(t);
        return p;
    }

    @SuppressWarnings("unchecked")
    private TableView<BooksDB.InvoiceData> buildHistoryTable() {
        TableView<BooksDB.InvoiceData> t = new TableView<>();
        t.getStyleClass().add("table-view");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setPlaceholder(new Label("No payment records found in the past 7 days."));

        TableColumn<BooksDB.InvoiceData, String> idCol = new TableColumn<>("Invoice ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(140);
        idCol.getStyleClass().add("col-left");
        idCol.setCellFactory(col -> new TableCell<BooksDB.InvoiceData, String>() {
            { getStyleClass().add("col-left"); }
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty ? null : s);
            }
        });

        TableColumn<BooksDB.InvoiceData, String> userCol = new TableColumn<>("Member");
        userCol.setCellValueFactory(new PropertyValueFactory<>("userId"));
        userCol.setPrefWidth(120);
        userCol.getStyleClass().add("col-left");
        userCol.setCellFactory(col -> new TableCell<BooksDB.InvoiceData, String>() {
            { getStyleClass().add("col-left"); }
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty ? null : s);
            }
        });

        TableColumn<BooksDB.InvoiceData, String> bookCol = new TableColumn<>("Book");
        bookCol.setCellValueFactory(new PropertyValueFactory<>("bookTitle"));
        bookCol.setPrefWidth(180);
        bookCol.getStyleClass().add("col-left");
        bookCol.setCellFactory(col -> new TableCell<BooksDB.InvoiceData, String>() {
            { getStyleClass().add("col-left"); }
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty ? null : s);
            }
        });

        TableColumn<BooksDB.InvoiceData, Double> amtCol = new TableColumn<>("Amount");
        amtCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amtCol.setPrefWidth(100);
        amtCol.getStyleClass().add("col-center");
        amtCol.setCellFactory(col -> new TableCell<BooksDB.InvoiceData, Double>() {
            { getStyleClass().add("col-center"); }
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : AppTheme.formatCurrency(item));
            }
        });

        TableColumn<BooksDB.InvoiceData, LocalDateTime> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateCol.setPrefWidth(140);
        dateCol.getStyleClass().add("col-center");
        dateCol.setCellFactory(col -> new TableCell<BooksDB.InvoiceData, LocalDateTime>() {
            { getStyleClass().add("col-center"); }
            @Override protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(DATETIME_FMT));
            }
        });

        TableColumn<BooksDB.InvoiceData, Void> actCol = new TableColumn<>("Actions");
        actCol.setPrefWidth(80);
        actCol.getStyleClass().add("col-center");
        actCol.setCellFactory(col -> new TableCell<BooksDB.InvoiceData, Void>() {
            private final Button btn = AppTheme.createIconButton(AppTheme.ICON_PRINT, "Reprint Receipt", AppTheme.ButtonStyle.GHOST);
            {
                btn.setOnAction(e -> reprintInvoice(getTableView().getItems().get(getIndex())));
                getStyleClass().add("col-center");
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        t.getColumns().setAll(idCol, userCol, bookCol, amtCol, dateCol, actCol);
        return t;
    }

    /**
     * FIX #24: Lets a regular user print one of their own paid invoices from the
     * "My Receipts" tab. Reuses the same InvoiceService path used by staff when
     * they reprint from the admin history table, but scoped to the current user.
     */
    private void printUserInvoice(BooksDB.InvoiceData data) {
        User user;
        try {
            user = UserService.getUserById(data.getUserId());
        } catch (Exception e) {
            if (toast != null) toast.showError("Could not load your account details.");
            return;
        }

        // Try to find the original issue record for richer invoice data; fall back to a
        // lightweight dummy if the record has already been purged from active circulation.
        IssueRecord record = issueRecords.stream()
                .filter(r -> r.getUserId().equals(data.getUserId())
                          && r.getIsbn().equals(data.getIsbn()))
                .findFirst()
                .orElseGet(() -> {
                    IssueRecord dummy = new IssueRecord(
                            data.getIsbn(), data.getBookTitle(), data.getUserId(),
                            LocalDate.now(), 1);
                    dummy.setFinePaid(true);
                    return dummy;
                });

        InvoiceService.processInvoiceActions(user, record, data.getAmount(), data.getId(), toast);
    }

    private void reprintInvoice(BooksDB.InvoiceData data) {
        // found; it never returns null. The previous null check was dead code and silently
        // swallowed the exception. Replace with a try-catch so errors surface correctly.
        User user;
        try {
            user = UserService.getUserById(data.getUserId());
        } catch (Exception e) {
            if (toast != null) toast.showError("Member not found.");
            return;
        }

        // Find matching record if possible for extra details, or create a dummy for the invoice
        IssueRecord record = issueRecords.stream()
                .filter(r -> r.getUserId().equals(data.getUserId()) && r.getIsbn().equals(data.getIsbn()))
                .findFirst()
                .orElseGet(() -> {
                    // Dummy record for reprinting if original was purged from circulation
                    IssueRecord dummy = new IssueRecord(data.getIsbn(), data.getBookTitle(), data.getUserId(), LocalDate.now(), 1);
                    dummy.setFinePaid(true);
                    return dummy;
                });

        InvoiceService.processInvoiceActions(user, record, data.getAmount(), data.getId(), toast);
    }

    private TableView<IssueRecord> buildSettlementsTable() {
        TableView<IssueRecord> t = new TableView<>();
        t.getStyleClass().add("table-view");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setPlaceholder(new Label("No outstanding settlements! 💸"));

        t.getColumns().add(colIR("Book Title",    r -> r.getBookTitle(), 200));
        t.getColumns().add(colIR("Borrower",      r -> r.getUserId(), 120));
        t.getColumns().add(colIR("Return Date",   r -> r.getReturnDate() != null ? r.getReturnDate().format(DATE_FMT) : "-", 110));
        t.getColumns().add(colIRC("Fine",         r -> AppTheme.formatCurrency(r.getRemainingFine()), 110));

        TableColumn<IssueRecord, Void> actC = new TableColumn<>("Actions");
        actC.getStyleClass().add("col-center");
        actC.setPrefWidth(90);
        actC.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("col-center"); }
            final Button payBtn = actionIconBtn(AppTheme.ICON_CARD, "Process Payment", "#F59E0B", AppTheme.ButtonStyle.AMBER);
            {
                payBtn.setOnAction(e -> processFinePayment(getTableRow().getItem(), getTableRow().getItem().getRemainingFine()));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(payBtn);
                }
            }
        });
        t.getColumns().add(actC);

        return t;
    }

    /**
     * Triggers an asynchronous batch process to send email reminders to all borrowers
     * currently in the overdue list.
     */
    private void bulkRemindOverdue() {
        List<IssueRecord> overdue = issueRecords.stream()
                .filter(r -> r.isOverdue() && r.getRemainingFine() > 0)
                .collect(java.util.stream.Collectors.toList());
        if (overdue.isEmpty()) {
            if (toast != null) toast.showInfo("No overdue books found.");
            return;
        }

        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Bulk Reminders");
        a.setHeaderText("Send reminders to " + overdue.size() + " borrowers?");
        a.setContentText("This will send automated email reminders to all users with overdue books.");
        AppTheme.applyTheme(a.getDialogPane());

        a.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt -> {
            if (toast != null) toast.showInfo("Sending " + overdue.size() + " reminders...");
            new Thread(() -> {
                try {
                    com.example.services.ReminderService.ReminderDispatchResult result =
                            com.example.services.ReminderService.sendOverdueReminders(overdue);
                    Platform.runLater(() -> {
                        if (toast == null) return;
                        int sent    = result.getSentCount();
                        int failed  = result.getFailures().size();
                        int skipped = result.getSkippedNoEmailCount();
                        if (failed == 0 && sent > 0) {
                            toast.showSuccess("Reminders sent to " + sent + " borrower(s)."
                                    + (skipped > 0 ? " " + skipped + " skipped (no email)." : ""));
                        } else if (sent == 0 && failed == 0) {
                            toast.showInfo("No reminders sent — all borrowers were skipped (no email on file).");
                        } else if (sent == 0) {
                            toast.showError("All " + failed + " reminder(s) failed to send.");
                        } else {
                            toast.showError(sent + " sent, " + failed + " failed"
                                    + (skipped > 0 ? ", " + skipped + " skipped" : "") + ".");
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        if (toast != null) toast.showError("Bulk reminder failed: " + ex.getMessage());
                    });
                }
            }, "bulk-reminders").start();
        });
    }

    private void buildOverdueTable(VBox p, HBox banner) {
        TableView<IssueRecord> ot = new TableView<>();
        ot.getStyleClass().add("table-view");
        ot.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        ot.setPlaceholder(new Label("No overdue books! 🎉"));

        ot.getColumns().add(colIR("Book Title",    r -> r.getBookTitle(), 200));
        ot.getColumns().add(colIR("Borrower",      r -> r.getUserId(), 120));
        ot.getColumns().add(colIR("Due Date",      r -> r.getDueDate().format(DATE_FMT), 110));
        ot.getColumns().add(colIRC("Days Overdue", r -> String.valueOf(r.getDaysOverdue()), 100));
        ot.getColumns().add(colIRC("Fine",         r -> AppTheme.formatCurrency(r.getRemainingFine()), 110));

        TableColumn<IssueRecord, Void> actC = overdueActionColumn();
        actC.getStyleClass().add("column-header-center");
        ot.getColumns().add(actC);

        ot.setItems(filteredOverdue);
        overdueTable = ot;
        VBox.setVgrow(ot, Priority.ALWAYS);


        // Export + Print buttons
        Button exportBtn = AppTheme.createIconTextButton(
                "Export CSV", AppTheme.ICON_UPLOAD, AppTheme.ButtonStyle.GHOST);
        exportBtn.setOnAction(e -> exportOverdueReport(filteredOverdue));
        exportBtn.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(filteredOverdue));


        Button printBtn = AppTheme.createIconTextButton(
                "Print Report", AppTheme.ICON_PRINT, AppTheme.ButtonStyle.GHOST);
        printBtn.setOnAction(e -> printOverdueReport(ot));
        printBtn.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(filteredOverdue));


        HBox bar2 = new HBox(8, printBtn, exportBtn);
        bar2.setAlignment(Pos.CENTER_RIGHT);

        p.getChildren().addAll(bar2, ot);
    }

    // ═══════════════════════════════════════════════════════════════
    // Issue Book Dialog
    // ═══════════════════════════════════════════════════════════════

    /**
     * Displays a professional, high-fidelity dialog for issuing books to members.
     * Includes real-time search for both books and users, and quantity validation.
     */
    private void showIssueDialog() {
        // Declared at method scope so both dlg.setOnShown and the categoryFilter
        // text listener (created later) share the same array reference.
        final boolean[] dialogReady = {false};

        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Issue Book");
        dlg.initOwner(getScene().getWindow());
        // Mark the dialog as ready AFTER it is fully shown on screen.
        // The categoryFilter text listener checks dialogReady before calling show()
        // so the popup can never open during dialog construction/layout.
        dlg.setOnShown(ev -> dialogReady[0] = true);

        DialogPane dp = dlg.getDialogPane();
        AppTheme.applyTheme(dp);
        dp.setPrefWidth(520);
        dp.setPrefHeight(660);

        VBox root = new VBox(20);
        root.setPadding(new Insets(24));

        Label heading = new Label("Issue Book to User");
        heading.setStyle("-fx-font-size:18px; -fx-font-weight:700; -fx-text-fill:" + textPrimary() + ";");

        // No local fixSpinner needed, using AppTheme.fixSpinner instead

        // ── Book picker ──────────────────────────────────────────
        Label bookLbl = fieldLabel("Select Book");
        TextField bookSearch = new TextField();
        bookSearch.setPromptText("Search by title, author or ISBN...");
        bookSearch.setStyle(inputStyle());

        // Category filter — editable ComboBox that filters its list as the user types
        List<String> allCategories = new java.util.ArrayList<>();
        allCategories.add("All categories");
        com.example.services.AppConfigurationService
                .getAvailableBookCategories(BookService.getAllBooks())
                .stream().filter(c -> c != null && !c.isBlank())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(allCategories::add);

        javafx.collections.ObservableList<String> masterCategories =
                javafx.collections.FXCollections.observableArrayList(allCategories);
        javafx.collections.transformation.FilteredList<String> filteredCategories =
                new javafx.collections.transformation.FilteredList<>(masterCategories, s -> true);

        ComboBox<String> categoryFilter = new ComboBox<>(filteredCategories);
        categoryFilter.setValue("All categories");
        categoryFilter.setEditable(true);
        categoryFilter.setPromptText("Filter by category…");
        categoryFilter.setVisibleRowCount(Math.min(10, allCategories.size() + 1));
        categoryFilter.setStyle(inputStyle());

        // NOTE: dialogReady is declared at the top of this method (before the Dialog).
        // The listener below reads it — must not re-declare it here.

        categoryFilter.getEditor().textProperty().addListener((obs, oldVal, text) -> {
            String typed = (text == null ? "" : text).trim();
            String lower = typed.toLowerCase();
            // Reset to SHOW ALL when the field is blank (user pressed backspace to clear).
            // Without this, FilteredList retains the previous predicate and shows nothing.
            filteredCategories.setPredicate(s ->
                    typed.isEmpty() || s.equalsIgnoreCase("All categories") || s.toLowerCase().contains(lower));

            Platform.runLater(() -> {
                if (!dialogReady[0]
                        || categoryFilter.getScene() == null
                        || categoryFilter.getScene().getWindow() == null
                        || !categoryFilter.getScene().getWindow().isShowing()) return;
                // Skip the initial setValue("All categories") trigger
                if (typed.equals("All categories")) return;
                if (!categoryFilter.isShowing() && !filteredCategories.isEmpty()) {
                    categoryFilter.show();
                } else if (categoryFilter.isShowing()) {
                    // Force popup to repaint so the filtered list updates visually
                    categoryFilter.hide();
                    categoryFilter.show();
                }
            });
        });

        // Both controls grow equally — uniform appearance
        bookSearch.setMaxWidth(Double.MAX_VALUE);
        bookSearch.setPrefWidth(0);
        bookSearch.setPrefHeight(40);
        bookSearch.setMinHeight(40);
        bookSearch.setMaxHeight(40);

        // For ComboBox, DO NOT set setMaxHeight — it causes the skin to apply an
        // external clip rect that makes typed text invisible (the editor TextField
        // inside is clipped). Instead, let HBox.fillHeight=true (default) call
        // resize() through the skin, which correctly lays out the internal editor.
        // The HBox row height is already locked to 40px via bookSearch's constraints.
        categoryFilter.setMaxWidth(Double.MAX_VALUE);
        categoryFilter.setPrefWidth(0);
        categoryFilter.setPrefHeight(40);

        HBox bookFilterRow = new HBox(8, bookSearch, categoryFilter);
        HBox.setHgrow(bookSearch, Priority.ALWAYS);
        HBox.setHgrow(categoryFilter, Priority.ALWAYS);

        ListView<Book> bookList = new ListView<>();
        bookList.setPrefHeight(130);
        bookList.setStyle(listSurfaceStyle());
        bookList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Book b, boolean empty) {
                super.updateItem(b, empty);
                if (empty || b == null) { setText(null); return; }
                setText(b.getTitle() + " — " + b.getAuthor() +
                        "  [" + b.getQuantity() + " available]");
            }
        });

        Label bookAvail = new Label();
        bookAvail.setStyle("-fx-font-size:12px;");

        Runnable refreshBooks = () -> {
            String q = bookSearch.getText().trim().toLowerCase();
            // Use the editor text for live filtering; fall back to the committed value
            String editorText = categoryFilter.getEditor().getText();
            String catFilter = (editorText != null && !editorText.isBlank())
                    ? editorText.trim()
                    : (categoryFilter.getValue() == null ? "" : categoryFilter.getValue().trim());
            boolean allCats = catFilter.isEmpty() || catFilter.equalsIgnoreCase("All categories");
            List<Book> all = BookService.getAllBooks().stream()
                    .filter(b -> b.getQuantity() > 0)
                    .filter(b -> allCats || (b.getCategory() != null
                            && b.getCategory().trim().equalsIgnoreCase(catFilter)))
                    .filter(b -> q.isEmpty() || b.getTitle().toLowerCase().contains(q)
                            || b.getAuthor().toLowerCase().contains(q)
                            || b.getIsbn().toLowerCase().contains(q))
                    .collect(Collectors.toList());
            bookList.setItems(FXCollections.observableArrayList(all));
        };
        refreshBooks.run();
        bookSearch.textProperty().addListener((o, old, v) -> refreshBooks.run());
        // On confirmed selection, reset the filter predicate then refresh books
        categoryFilter.valueProperty().addListener((o, old, v) -> {
            filteredCategories.setPredicate(s -> true);
            refreshBooks.run();
        });

        // --- Persistence for selections ---
        Set<Book> selectedBooks = new HashSet<>();
        Map<String, Spinner<Integer>> quantityMap = new HashMap<>();
        Label selectedHdr = fieldLabel("Selected Books & Quantities");
        VBox selectedBooksBox = new VBox(10);
        selectedBooksBox.setPadding(new Insets(10));
        selectedBooksBox.setStyle("-fx-background-color:" + (AppTheme.darkMode ? "#1E293B" : "#F8FAFC") +
                "; -fx-background-radius:8px; -fx-border-color:" + (AppTheme.darkMode ? "#334155" : "#E2E8F0") +
                "; -fx-border-width:1;");

        // --- Related Requests List ---
        Label requestsHdr = fieldLabel("Pending Requests for Selected Books");
        ListView<BorrowRequest> relatedRequestsList = new ListView<>();
        relatedRequestsList.setPrefHeight(100);
        relatedRequestsList.setStyle(listSurfaceStyle());
        relatedRequestsList.setPlaceholder(new Label("No matching requests found"));
        relatedRequestsList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(BorrowRequest r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) { setGraphic(null); return; }
                VBox box = new VBox(2);
                Label u = new Label(r.getUserId() + " — " + r.getBookTitle());
                u.setStyle("-fx-font-weight:bold; -fx-font-size:12px; -fx-text-fill:" + textPrimary() + ";");
                Label d = new Label("Requested " + r.getQuantity() + " copies on " + r.getRequestedAt().toLocalDate());
                d.setStyle("-fx-font-size:11px; -fx-text-fill:" + textMuted() + ";");
                box.getChildren().addAll(u, d);
                setGraphic(box);
            }
        });

        // Helper to update the selected books UI
        final Runnable[] updateSelectedUI = {null};
        updateSelectedUI[0] = () -> {
            selectedBooksBox.getChildren().clear();
            if (selectedBooks.isEmpty()) {
                Label placeholder = new Label("No books selected");
                placeholder.setStyle("-fx-text-fill:" + textMuted() + "; -fx-font-style:italic;");
                selectedBooksBox.getChildren().add(placeholder);
                bookAvail.setText("");
                relatedRequestsList.getItems().clear();
            } else {
                bookAvail.setText(selectedBooks.size() + " book(s) selected");
                bookAvail.setStyle("-fx-font-size:12px; -fx-text-fill:#16A34A; -fx-font-weight:600;");

                for (Book sb : selectedBooks) {
                    HBox row = new HBox(12);
                    row.setAlignment(Pos.CENTER_LEFT);

                    Label title = new Label(sb.getTitle());
                    title.setStyle("-fx-font-size:13px; -fx-text-fill:" + textPrimary() + "; -fx-font-weight:600;");
                    HBox.setHgrow(title, Priority.ALWAYS);

                    Spinner<Integer> qSpin = quantityMap.computeIfAbsent(sb.getIsbn(), k -> {
                        Spinner<Integer> s = new Spinner<>(1, sb.getQuantity(), 1);
                        s.setEditable(true);
                        s.setPrefWidth(90);
                        s.getStyleClass().add("themed-spinner");
                        s.getEditor().setStyle("-fx-font-size:13px; -fx-alignment:CENTER;");
                        AppTheme.fixSpinner(s);
                        return s;
                    });

                    Button removeBtn = AppTheme.createIconButton(AppTheme.ICON_CLOSE, "Remove", AppTheme.ButtonStyle.GHOST);
                    removeBtn.setOnAction(e -> {
                        selectedBooks.remove(sb);
                        quantityMap.remove(sb.getIsbn());
                        Platform.runLater(() -> updateSelectedUI[0].run());
                    });

                    row.getChildren().addAll(title, qSpin, removeBtn);
                    selectedBooksBox.getChildren().add(row);
                }

                // Update related requests
                List<String> isbns = selectedBooks.stream().map(Book::getIsbn).collect(Collectors.toList());
                relatedRequestsList.setItems(FXCollections.observableArrayList(com.example.services.BorrowRequestService.getPendingRequestsForBooks(isbns)));
            }
        };

        bookList.getSelectionModel().selectedItemProperty().addListener((o, old, v) -> {
            if (v != null) {
                if (selectedBooks.contains(v)) {
                    selectedBooks.remove(v);
                    quantityMap.remove(v.getIsbn());
                } else {
                    selectedBooks.add(v);
                }
                updateSelectedUI[0].run();
                // Clear selection to allow re-selection if it pops up in search again
                Platform.runLater(() -> bookList.getSelectionModel().clearSelection());
            }
        });

        updateSelectedUI[0].run();

        // ── User picker ──────────────────────────────────────────
        Label userLbl = fieldLabel("Select User");
        TextField userSearch = new TextField();
        userSearch.setPromptText("Search by username or name...");
        userSearch.setStyle(inputStyle());

        ListView<User> userListView = new ListView<>();
        userListView.setPrefHeight(120);
        userListView.setStyle(listSurfaceStyle());
        userListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) { setText(null); return; }
                setText(u.getUserId() + "  —  " + u.getFullName());
            }
        });

        Runnable refreshUsers = () -> {
            String q = userSearch.getText().trim().toLowerCase();
            List<User> all = UserService.getAllUsers().stream()
                    .filter(u -> q.isEmpty()
                            || u.getUserId().toLowerCase().contains(q)
                            || u.getFullName().toLowerCase().contains(q))
                    .collect(Collectors.toList());
            userListView.setItems(FXCollections.observableArrayList(all));

            // Auto-select if exactly one match or exact ID match
            if (!q.isEmpty()) {
                for (User u : all) {
                    if (u.getUserId().equalsIgnoreCase(q)) {
                        userListView.getSelectionModel().select(u);
                        break;
                    }
                }
            }
        };
        refreshUsers.run();
        userSearch.textProperty().addListener((o, old, v) -> refreshUsers.run());

        userListView.getSelectionModel().selectedItemProperty().addListener((o, old, v) -> {
            // Prevent selection change if the UI is locked by a pending request
            if (userSearch.isDisable() && old != null && v != null && v != old) {
                Platform.runLater(() -> userListView.getSelectionModel().select(old));
                return;
            }
            if (v != null && !userSearch.isDisable()) {
                userSearch.setText(v.getUserId());
            }
        });

        relatedRequestsList.setOnMouseClicked(e -> {
            if (relatedRequestsList.getSelectionModel().getSelectedIndex() == -1) return;
            // Toggle selection if clicking the same item
        });

        // Add a Clear button to userSearch row
        Button clearUserSearchBtn = AppTheme.createIconButton(AppTheme.ICON_CLOSE, "Clear Selection", AppTheme.ButtonStyle.GHOST);
        clearUserSearchBtn.setOnAction(e -> {
            relatedRequestsList.getSelectionModel().clearSelection();
            userSearch.setDisable(false);
            userSearch.setText("");
            userListView.getSelectionModel().clearSelection();
        });
        HBox userSearchContainer = new HBox(8, userSearch, clearUserSearchBtn);
        HBox.setHgrow(userSearch, Priority.ALWAYS);

        // Reactive minimum date: null = no lower bound; dynamically updated based
        // on the selected user and selected books matching any pending requests.
        final javafx.beans.property.ObjectProperty<LocalDate> minIssueDate =
                new javafx.beans.property.SimpleObjectProperty<>(null);

        Runnable recalculateMinIssueDate = () -> {
            User user = userListView.getSelectionModel().getSelectedItem();
            if (user == null || selectedBooks.isEmpty()) {
                minIssueDate.set(null);
                return;
            }
            List<BorrowRequest> userReqs = com.example.services.BorrowRequestService.getRequestsForUser(user.getUserId());
            LocalDate maxReqDate = null;
            for (Book b : selectedBooks) {
                for (BorrowRequest r : userReqs) {
                    if (r.isPending() && r.getIsbn().equals(b.getIsbn())) {
                        LocalDate reqDate = r.getRequestedAt().toLocalDate();
                        if (maxReqDate == null || reqDate.isAfter(maxReqDate)) {
                            maxReqDate = reqDate;
                        }
                    }
                }
            }
            minIssueDate.set(maxReqDate);
        };

        userListView.getSelectionModel().selectedItemProperty().addListener((o, old, v) -> recalculateMinIssueDate.run());
        relatedRequestsList.itemsProperty().addListener((o, old, v) -> recalculateMinIssueDate.run());

        relatedRequestsList.getSelectionModel().selectedItemProperty().addListener((o, old, r) -> {
            // Prevent clicking another request to bypass the lock; user MUST click the X icon first
            if (userSearch.isDisable() && old != null && r != null && r != old) {
                Platform.runLater(() -> relatedRequestsList.getSelectionModel().select(old));
                return;
            }

            if (r != null) {
                User user = UserService.getUserById(r.getUserId());
                if (user != null) {
                    userListView.getSelectionModel().select(user);
                    userListView.scrollTo(user);
                    userSearch.setText(user.getUserId());
                }
                if (quantityMap.containsKey(r.getIsbn())) {
                    quantityMap.get(r.getIsbn()).getValueFactory().setValue(r.getQuantity());
                }
                // Lock everything to prevent overriding the request details
                userSearch.setDisable(true);
                userListView.setDisable(true);
                bookSearch.setDisable(true);
                bookList.setDisable(true);
                userSearch.setOpacity(0.5);
                userListView.setOpacity(0.8);
                bookSearch.setOpacity(0.5);
                bookList.setOpacity(0.8);
                selectedBooksBox.setDisable(true);
                selectedBooksBox.setOpacity(0.8);
            } else {
                userSearch.setDisable(false);
                userListView.setDisable(false);
                bookSearch.setDisable(false);
                bookList.setDisable(false);
                userSearch.setOpacity(1.0);
                userListView.setOpacity(1.0);
                bookSearch.setOpacity(1.0);
                bookList.setOpacity(1.0);
                selectedBooksBox.setDisable(false);
                selectedBooksBox.setOpacity(1.0);
            }
        });

        Label issueDateLbl = fieldLabel("Issue Date");

        // Supplier that generates a NEW Callback instance every time.
        // JavaFX DatePicker caches cells and only rebuilds them if setDayCellFactory
        // receives a mathematically different object reference. Reusing the same
        // instance causes it to ignore updates and leave invalid dates clickable.
        java.util.function.Supplier<javafx.util.Callback<DatePicker, DateCell>> cellFactorySupplier = () -> picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                LocalDate min = minIssueDate.get();
                // Block: after today OR before the request date (when a request is selected)
                boolean blocked = empty || date.isAfter(LocalDate.now())
                        || (min != null && date.isBefore(min));
                setDisable(blocked);
                if (!empty && min != null && date.isBefore(min)) {
                    setStyle("-fx-background-color:#FEE2E2; -fx-text-fill:#9CA3AF;");
                } else if (!empty && date.isAfter(LocalDate.now())) {
                    setStyle("-fx-opacity: 0.4;");
                } else {
                    setStyle("");
                }
            }
        };

        DatePicker issueDatePicker = new DatePicker(LocalDate.now());
        issueDatePicker.setDayCellFactory(cellFactorySupplier.get());

        // When the min date changes, ensure the current value is still within range
        // AND assign a mathematically NEW factory to force JavaFX to discard its cell cache.
        minIssueDate.addListener((obs, oldMin, newMin) -> {
            if (newMin != null && issueDatePicker.getValue() != null
                    && issueDatePicker.getValue().isBefore(newMin)) {
                issueDatePicker.setValue(newMin);
            }
            issueDatePicker.setDayCellFactory(cellFactorySupplier.get());
            if (issueDatePicker.isShowing()) {
                issueDatePicker.hide();
                issueDatePicker.show();
            }
        });
        issueDatePicker.setEditable(false);
        issueDatePicker.setConverter(new javafx.util.StringConverter<LocalDate>() {
            private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            @Override public String toString(LocalDate date) { return (date != null) ? formatter.format(date) : ""; }
            @Override public LocalDate fromString(String string) { return (string != null && !string.isEmpty()) ? LocalDate.parse(string, formatter) : null; }
        });
        issueDatePicker.setPrefWidth(180);
        issueDatePicker.setPrefHeight(30);
        issueDatePicker.setStyle("-fx-font-size: 13px; -fx-padding: 0 4;");

        Label loanDaysLbl = fieldLabel("Loan Period (Days)");
        Spinner<Integer> loanDaysSpin = new Spinner<>(1, 365, BookService.getLoanPeriodDays());
        loanDaysSpin.setEditable(true);
        loanDaysSpin.getStyleClass().add("themed-spinner");
        loanDaysSpin.setPrefWidth(100);
        loanDaysSpin.setPrefHeight(30);
        loanDaysSpin.getEditor().setStyle("-fx-font-size:13px; -fx-alignment:CENTER;");
        AppTheme.fixSpinner(loanDaysSpin);

        Label testingHint = new Label("Search and click books to toggle selection. Selections persist across searches.");
        testingHint.setStyle("-fx-font-size:12px; -fx-text-fill:" + textMuted() + ";");
        testingHint.setWrapText(true);

        // ── Error feedback ────────────────────────────────────────
        Label errLbl = new Label();
        errLbl.setStyle("-fx-font-size:13px; -fx-text-fill:#DC2626;");
        errLbl.setVisible(false);

        HBox dateLoanBox = new HBox(24,
                new VBox(6, issueDateLbl, issueDatePicker),
                new VBox(6, loanDaysLbl, loanDaysSpin));
        dateLoanBox.setAlignment(Pos.BOTTOM_LEFT);

        root.getChildren().addAll(
                heading,
                bookLbl, bookFilterRow, bookList, bookAvail,
                selectedHdr, selectedBooksBox,
                requestsHdr, relatedRequestsList,
                userLbl, userSearchContainer, userListView,
                dateLoanBox,
                testingHint,
                errLbl
        );
        ScrollPane formScroll = new ScrollPane(root);
        formScroll.setFitToWidth(true);
        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScroll.setStyle("-fx-background:transparent; -fx-background-color:transparent;");
        dp.setContent(formScroll);

        ButtonType issueType = new ButtonType("Issue", ButtonBar.ButtonData.OK_DONE);
        dp.getButtonTypes().addAll(ButtonType.CANCEL, issueType);
        Button issueBtn = (Button) dp.lookupButton(issueType);
        issueBtn.getStyleClass().add("btn-primary");
        ((Button) dp.lookupButton(ButtonType.CANCEL)).getStyleClass().add("btn-secondary");

        issueBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            User user = userListView.getSelectionModel().getSelectedItem();
            LocalDate date = issueDatePicker.getValue();
            if (selectedBooks.isEmpty()) { errLbl.setText("Please select at least one book."); errLbl.setVisible(true); ev.consume(); return; }
            if (user == null)       { errLbl.setText("Please select a user."); errLbl.setVisible(true); ev.consume(); return; }
            if (date == null)       { errLbl.setText("Please choose an issue date."); errLbl.setVisible(true); ev.consume(); return; }

            LocalDate minDate = minIssueDate.get();
            if (minDate != null && date.isBefore(minDate)) {
                errLbl.setText("Issue date cannot be earlier than the request date (" + minDate + ").");
                errLbl.setVisible(true);
                ev.consume();
                return;
            }

            // Proactive validation: Even if the librarian didn't select the request from the list,
            // we must block dates earlier than the request date if this issue will auto-approve a request.
            List<BorrowRequest> userReqs = com.example.services.BorrowRequestService.getRequestsForUser(user.getUserId());
            for (Book b : selectedBooks) {
                for (BorrowRequest r : userReqs) {
                    if (r.isPending() && r.getIsbn().equals(b.getIsbn())) {
                        LocalDate reqDate = r.getRequestedAt().toLocalDate();
                        if (date.isBefore(reqDate)) {
                            errLbl.setText("Cannot backdate issue: Book was requested on " + reqDate + ".");
                            errLbl.setVisible(true);
                            ev.consume();
                            return;
                        }
                    }
                }
            }

            errLbl.setVisible(false);
            try {
                String processedBy = com.example.services.UserService.getCurrentUserId();
                if (processedBy == null) processedBy = "Admin";

                for (Book b : selectedBooks) {
                    int qty = quantityMap.get(b.getIsbn()).getValue();
                    BookService.issueBookToUser(b.getIsbn(), user.getUserId(), qty, date, loanDaysSpin.getValue());

                    // Auto-approve matching pending request if exists
                    List<BorrowRequest> pendingReqs = com.example.services.BorrowRequestService.getRequestsForUser(user.getUserId());
                    for (BorrowRequest r : pendingReqs) {
                        if (r.isPending() && r.getIsbn().equals(b.getIsbn())) {
                            com.example.services.BorrowRequestService.markRequestAsApproved(r.getRequestId(), processedBy);
                        }
                    }
                }
                toast.showSuccess("Successfully issued " + selectedBooks.size() + " book(s) to " + user.getUserId());
                onRefresh.run();
            } catch (Exception ex) {
                errLbl.setText(ex.getMessage()); errLbl.setVisible(true); ev.consume();
            }
        });

        dlg.setResultConverter(b -> null);
        dlg.showAndWait();
    }

    // ═══════════════════════════════════════════════════════════════
    // Actions
    // ═══════════════════════════════════════════════════════════════

    private void returnBook(IssueRecord r) {
        int issuedQty = r.getQuantity();
        double fine   = r.calculateFine();

        // When more than 1 copy was issued, let the librarian choose how many to return.
        final int[] returnQty = {issuedQty};

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Return Book");
        dlg.setHeaderText("Return: " + r.getBookTitle());

        VBox content = new VBox(12);
        content.setPadding(new Insets(16, 20, 8, 20));
        content.getChildren().add(new Label("Borrower: " + r.getUserId()));

        if (issuedQty > 1) {
            HBox qtyRow = new HBox(10);
            qtyRow.setAlignment(Pos.CENTER_LEFT);
            Label qtyLbl = new Label("Return quantity (issued: " + issuedQty + "):");
            Spinner<Integer> qtySpinner = new Spinner<>(1, issuedQty, issuedQty);
            qtySpinner.setEditable(true);
            AppTheme.fixSpinner(qtySpinner);
            qtySpinner.valueProperty().addListener((ob, ov, nv) -> returnQty[0] = nv);
            qtyRow.getChildren().addAll(qtyLbl, qtySpinner);
            content.getChildren().add(qtyRow);
        } else {
            content.getChildren().add(new Label("Qty: " + issuedQty));
        }

        if (fine > 0) {
            Label fineLbl = new Label("Fine outstanding: " + AppTheme.formatCurrency(fine));
            fineLbl.setStyle("-fx-text-fill:#DC2626; -fx-font-weight:700;");
            content.getChildren().add(fineLbl);
        }

        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        AppTheme.applyTheme(dlg.getDialogPane());

        dlg.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt -> {
            try {
                double calculatedFine = r.calculateFine();
                BookService.returnBookFromUser(r.getIsbn(), r.getUserId(), returnQty[0]);

                if (calculatedFine > 0 && returnQty[0] == issuedQty) {
                    // Show fine dialog only when all copies are returned
                    showFinePaymentDialog(r, calculatedFine);
                } else {
                    if (toast != null) {
                        String msg = returnQty[0] == issuedQty
                                ? "Book returned successfully."
                                : returnQty[0] + " of " + issuedQty + " copies returned.";
                        toast.showSuccess(msg);
                    }
                }
                if (onRefresh != null) onRefresh.run();
            } catch (Exception ex) {
                if (toast != null) toast.showError("Return failed: " + ex.getMessage());
            }
        });
    }

    private void showFinePaymentDialog(IssueRecord r, double fine) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Fine Payment");
        a.setHeaderText("Fine Outstanding: " + AppTheme.formatCurrency(fine));
        a.setContentText("The book has been returned. Would you like to process the fine payment and generate an invoice?");

        ButtonType payBtn = new ButtonType("Pay & Invoice", ButtonBar.ButtonData.OK_DONE);
        ButtonType laterBtn = new ButtonType("Pay Later", ButtonBar.ButtonData.CANCEL_CLOSE);
        a.getButtonTypes().setAll(payBtn, laterBtn);

        AppTheme.applyTheme(a.getDialogPane());
        a.showAndWait().ifPresent(type -> {
            if (type == payBtn) {
                processFinePayment(r, fine);
            } else {
                // Use the correct tab name for the current user's role
                String tabName = isStaff ? "'Settlements'" : "'My Fines'";
                if (toast != null) toast.showSuccess("Book returned. Fine moved to your " + tabName + " tab.");
            }
        });
    }

    private void processFinePayment(IssueRecord r, double fine) {
        try {
            if (isStaff) {
                // Staff process payments directly — no approval needed
                User user = UserService.getUserById(r.getUserId());
                double remaining = r.isReturned()
                        ? r.getRemainingFine()
                        : r.calculateFine() - r.getPaidAmount();
                InvoiceService.generateAndHandleInvoice(user, r, remaining, toast, this::refreshAfterPayment);
            } else {
                // Regular users: submit a payment-approval request to all admins/librarians.
                // The payment is NOT finalised until a staff member approves it.
                submitUserPaymentRequest(r, fine);
                if (onRefresh != null) onRefresh.run();
            }
        } catch (Exception e) {
            if (toast != null) toast.showError("Payment processing failed: " + e.getMessage());
        }
    }

    private void refreshAfterPayment() {
        Platform.runLater(() -> {
            if (refreshAllData != null) {
                refreshAllData.run();
            } else if (onRefresh != null) {
                onRefresh.run();
            }
        });
    }

    /** Shows a confirmation dialog then submits the request for staff approval. */
    private void submitUserPaymentRequest(IssueRecord r, double fine) {
        javafx.scene.control.Dialog<Double> dlg = new javafx.scene.control.Dialog<>();
        dlg.setTitle("Pay Fine");
        dlg.setHeaderText("Submit payment for \"" + r.getBookTitle() + "\"");
        if (dlg.getDialogPane().getScene() != null && dlg.getDialogPane().getScene().getWindow() != null)
            com.example.application.ui.AppTheme.applyTheme(dlg.getDialogPane());

        javafx.scene.control.ButtonType submitType = new javafx.scene.control.ButtonType(
                "Submit for Approval", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        javafx.scene.control.ButtonType cancelType = new javafx.scene.control.ButtonType(
                "Cancel", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        dlg.getDialogPane().getButtonTypes().addAll(submitType, cancelType);

        double maxOwed = fine > 0 ? fine : (r.isReturned() ? r.getRemainingFine() : r.calculateFine() - r.getPaidAmount());
        javafx.scene.control.TextField amountField = new javafx.scene.control.TextField(
                String.format("%.2f", maxOwed));
        amountField.setStyle(com.example.application.ui.CirculationView.inputStyle());
        Label info = new Label("Amount fine to pay (max " +
                com.example.application.ui.AppTheme.formatCurrency(maxOwed) + "):");
        Label note = new Label("Your payment request will be sent to the library staff for approval.\nYou will be notified once it is processed.");
        note.setWrapText(true);
        note.setStyle("-fx-font-size:11px; -fx-text-fill:" +
                (com.example.application.ui.AppTheme.darkMode ? "#94A3B8" : "#64748B") + ";");

        VBox content = new VBox(8, info, amountField, note);
        content.setPadding(new Insets(16));
        dlg.getDialogPane().setContent(content);

        // Validate on submit
        javafx.scene.Node submitBtn = dlg.getDialogPane().lookupButton(submitType);
        submitBtn.getStyleClass().add("btn-primary");
        double finalMaxOwed = maxOwed;
        submitBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                double amt = Double.parseDouble(amountField.getText().trim());
                if (amt <= 0 || amt > finalMaxOwed + 0.01) {
                    amountField.setStyle(com.example.application.ui.CirculationView.inputStyle()
                            + " -fx-border-color:#EF4444;");
                    ev.consume();
                }
            } catch (NumberFormatException e) {
                ev.consume();
            }
        });

        dlg.setResultConverter(bt -> {
            if (bt == submitType) {
                try { return Double.parseDouble(amountField.getText().trim()); }
                catch (Exception e) { return null; }
            }
            return null;
        });

        dlg.showAndWait().ifPresent(amount -> {
            try {
                com.example.services.PaymentApprovalService.submitRequest(currentUser, r, amount);
                if (toast != null) toast.showSuccess(
                        "Payment request submitted! Staff will review and approve it shortly.");
            } catch (Exception ex) {
                if (toast != null) toast.showError("Could not submit request: " + ex.getMessage());
            }
        });
    }

    private void renewBook(IssueRecord r) {
        int days = BookService.getLoanPeriodDays();
        if (r.renew(days)) {
            // BUG FIX: The IssueRecord was modified in memory but never saved to disk,
            // so every renewal was silently lost on application restart.
            // We must explicitly persist the books database after mutating the record.
            com.example.entities.BooksDB.getInstance().saveAllData();
            if (toast != null) toast.showSuccess("Renewed! New due date: " +
                    r.getDueDate().format(DATE_FMT));
            if (onRefresh != null) onRefresh.run();
        } else {
            if (toast != null) toast.showError("Cannot renew — max renewals reached.");
        }
    }

    private void approveRequest(BorrowRequest req) {
        try {
            BookService.approveBorrowRequest(req.getRequestId(), currentUser);
            if (toast != null) toast.showSuccess("Request approved!");
            if (onRefresh != null) onRefresh.run();
        } catch (Exception ex) {
            if (toast != null) toast.showError("Approve failed: " + ex.getMessage());
        }
    }

    private void rejectRequest(BorrowRequest req) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Reject Request");
        dialog.setHeaderText("Reject request for: " + req.getBookTitle());
        AppTheme.applyTheme(dialog.getDialogPane());

        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Enter a rejection reason (optional)");
        reasonArea.setWrapText(true);
        reasonArea.setPrefRowCount(6);

        dialog.getDialogPane().setContent(reasonArea);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setResultConverter(button -> button == ButtonType.OK ? reasonArea.getText() : null);

        dialog.showAndWait().ifPresent(reason -> {
            try {
                BookService.rejectBorrowRequest(req.getRequestId(), currentUser, reason);
                if (toast != null) toast.showSuccess("Request rejected.");
                if (onRefresh != null) onRefresh.run();
            } catch (Exception ex) {
                if (toast != null) toast.showError("Reject failed: " + ex.getMessage());
            }
        });
    }

    /** Opens the OS print dialog and prints the overdue TableView. */
    private void printOverdueReport(javafx.scene.control.TableView<IssueRecord> table) {
        javafx.print.PrinterJob job = javafx.print.PrinterJob.createPrinterJob();
        if (job == null) {
            if (toast != null) toast.showWarning(
                    "No printer found. Install a printer driver, or use the Export CSV button instead.");
            return;
        }

        boolean proceed = job.showPrintDialog(getScene().getWindow());
        if (!proceed) {
            job.cancelJob();
            return;
        }

        javafx.print.PageLayout layout = job.getJobSettings().getPageLayout();
        double printW = layout.getPrintableWidth();
        double printH = layout.getPrintableHeight();

        java.util.List<javafx.scene.Node> pages = new java.util.ArrayList<>();
        VBox currentPage = createPrintPage(printW, printH);
        double currentHeight = 100; // Initial header height estimation

        for (IssueRecord r : table.getItems()) {
            HBox row = createPrintRow(r, printW);
            double rowH = 30; // Estimated row height
            if (currentHeight + rowH > printH - 50) {
                pages.add(currentPage);
                currentPage = createPrintPage(printW, printH);
                currentHeight = 100;
            }
            currentPage.getChildren().add(row);
            currentHeight += rowH;
        }
        if (currentPage.getChildren().size() > 2) { // 2 = title + header row
            pages.add(currentPage);
        }

        boolean success = true;
        for (javafx.scene.Node page : pages) {
            success &= job.printPage(page);
        }

        if (success) {
            job.endJob();
            if (toast != null) toast.showSuccess("Overdue report sent to printer.");
        } else {
            job.cancelJob();
            if (toast != null) toast.showError("Printing failed or was cancelled.");
        }
    }

    private VBox createPrintPage(double width, double height) {
        VBox page = new VBox(10);
        page.setPadding(new Insets(20));
        page.setPrefSize(width, height);
        page.setStyle("-fx-background-color: white;");

        Label title = new Label("Overdue Books Report - " + java.time.LocalDate.now());
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: black;");

        HBox headerRow = new HBox(10);
        headerRow.setStyle("-fx-border-color: black; -fx-border-width: 0 0 1 0; -fx-padding: 0 0 5 0;");
        headerRow.getChildren().addAll(
                createPrintCell("Book Title", width * 0.4, true),
                createPrintCell("Borrower", width * 0.2, true),
                createPrintCell("Due Date", width * 0.2, true),
                createPrintCell("Fine", width * 0.15, true)
        );

        page.getChildren().addAll(title, headerRow);
        return page;
    }

    private HBox createPrintRow(IssueRecord r, double width) {
        HBox row = new HBox(10);
        row.setStyle("-fx-border-color: #EEEEEE; -fx-border-width: 0 0 1 0; -fx-padding: 5 0 5 0;");
        row.getChildren().addAll(
                createPrintCell(r.getBookTitle(), width * 0.4, false),
                createPrintCell(r.getUserId(), width * 0.2, false),
                createPrintCell(r.getDueDate().format(DATE_FMT), width * 0.2, false),
                createPrintCell(AppTheme.formatCurrency(r.calculateFine()), width * 0.15, false)
        );
        return row;
    }

    private Label createPrintCell(String text, double width, boolean bold) {
        Label l = new Label(text);
        l.setPrefWidth(width);
        l.setWrapText(true);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: black;" + (bold ? " -fx-font-weight: bold;" : ""));
        return l;
    }

    private void exportOverdueReport(ObservableList<IssueRecord> data) {        try {
        java.nio.file.Path p = com.example.services.ReportExportService
                .exportOverdueReportCsv(data);
        if (toast != null) toast.showSuccess("Exported to: " + p.toAbsolutePath());
    } catch (Exception ex) {
        if (toast != null) toast.showError("Export failed: " + ex.getMessage());
    }
    }

    private TableColumn<IssueRecord, Void> overdueActionColumn() {
        TableColumn<IssueRecord, Void> actionColumn = new TableColumn<>("Actions");
        actionColumn.getStyleClass().add("col-center");
        actionColumn.setPrefWidth(125);
        actionColumn.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("col-center"); }
            final Button emailBtn = actionIconBtn(AppTheme.ICON_MAIL, "Send overdue reminder", "#06B6D4", AppTheme.ButtonStyle.CYAN);
            final Button contactBtn = actionIconBtn(AppTheme.ICON_USER, "View borrower contact", "#6366F1", AppTheme.ButtonStyle.INDIGO);
            final Button invBtn = actionIconBtn(AppTheme.ICON_SAVE, "Generate early invoice", "#F59E0B", AppTheme.ButtonStyle.AMBER);
            final HBox box = new HBox(4, emailBtn, contactBtn, invBtn);

            {
                box.setAlignment(Pos.CENTER);
                emailBtn.setOnAction(event -> sendOverdueReminder(getTableView().getItems().get(getIndex()), emailBtn));
                contactBtn.setOnAction(event -> showBorrowerContact(getTableView().getItems().get(getIndex())));
                invBtn.setOnAction(event -> {
                    IssueRecord r = getTableView().getItems().get(getIndex());
                    try {
                        User user = UserService.getUserById(r.getUserId());
                        InvoiceService.generateAndHandleInvoice(user, r, r.calculateFine(), toast, refreshAllData);
                    } catch (Exception ex) {
                        if (toast != null) toast.showError("Failed to load user: " + ex.getMessage());
                    }
                });
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }

                IssueRecord record = getTableRow().getItem();
                // BUG FIX: UserService.getUserById() throws UserException when the user
                // is not found. Throwing inside a TableCell.updateItem() callback crashes
                // the entire table rendering loop. Wrap in try-catch and treat a missing
                // user the same as a user with no email (disable the email button).
                User borrower = null;
                try {
                    borrower = UserService.getUserById(record.getUserId());
                } catch (Exception e) {
                    java.util.logging.Logger.getLogger("CirculationView")
                            .log(java.util.logging.Level.FINE,
                                    "Could not load borrower " + record.getUserId() + " for email button: " + e.getMessage());
                }
                boolean systemCanEmail = com.example.services.AppConfigurationService.getConfiguration().isEmailConfigured();
                // FIX #20: decrypt the email with master-key fallback before checking whether
                // the borrower actually has a valid address — raw ciphertext would otherwise
                // always appear non-blank, enabling the button even with no real address.
                String rawEmail = borrower != null ? borrower.getEmail() : null;
                String decEmail = rawEmail != null
                        ? com.example.services.SecurityProvider.decryptUserField(rawEmail, record.getUserId(), "", "")
                        : null;
                boolean userHasEmail = decEmail != null && !decEmail.isBlank() && decEmail.contains("@");
                boolean isReturned = record.isReturned();

                emailBtn.setDisable(!systemCanEmail || !userHasEmail || isReturned);
                setGraphic(box);
            }
        });
        return actionColumn;
    }

    private void sendOverdueReminder(IssueRecord record, Button triggerButton) {
        if (toast != null) {
            toast.showInfo("Sending reminder email…");
        }
        Platform.runLater(() -> triggerButton.setDisable(true));
        new Thread(() -> {
            try {
                User user = UserService.getUserById(record.getUserId());
                // FIX #20: decrypt email with master-key fallback before checking/displaying it
                String decryptedEmail = com.example.services.SecurityProvider.decryptUserField(
                        user.getEmail(), user.getUserId(), "", "");
                if (decryptedEmail == null || decryptedEmail.isBlank()) {
                    throw new IllegalStateException("Borrower does not have an email address on file.");
                }

                ReminderService.sendOverdueReminder(user, List.of(record));
                final String displayEmail = decryptedEmail;
                Platform.runLater(() -> {
                    triggerButton.setDisable(false);
                    if (toast != null) {
                        toast.showSuccess("Reminder sent to " + displayEmail);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    triggerButton.setDisable(false);
                    if (toast != null) {
                        toast.showError("Reminder failed: " + ReminderService.toUserMessage(ex));
                    }
                });
            }
        }, "overdue-reminder").start();
    }

    private void showBorrowerContact(IssueRecord record) {
        try {
            User user = UserService.getUserById(record.getUserId());
            // FIX #20: email and contactNumber are stored encrypted in the serialised User
            // object. When a staff member looks up another user, unlockProfile() is never
            // called for that user, so the raw ciphertext is still in the fields.
            // decryptUserField with empty password skips the user-key path and falls back
            // to the system master-key decrypt, which is the correct approach for staff
            // viewing another member's PII.
            String email   = com.example.services.SecurityProvider.decryptUserField(
                    user.getEmail(), user.getUserId(), "", "");
            String contact = com.example.services.SecurityProvider.decryptUserField(
                    user.getContactNumber(), user.getUserId(), "", "");

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Borrower Contact");
            alert.setHeaderText(user.getDisplayName());
            alert.setContentText("Email: " + valueOrPlaceholder(email) +
                    "\nMobile: " + valueOrPlaceholder(contact));
            AppTheme.applyTheme(alert.getDialogPane());
            alert.showAndWait();
        } catch (Exception ex) {
            if (toast != null) {
                toast.showError("Could not load borrower contact: " + ex.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Binding
    // ═══════════════════════════════════════════════════════════════

    private void bind() {
        issuesTable.setItems(filteredIssues);
        requestsTable.setItems(sortedRequests);
        if (overdueTable != null) overdueTable.setItems(filteredOverdue);
        if (userOverdueTable != null) userOverdueTable.setItems(filteredUserOverdue);
        if (settlementsTable != null) settlementsTable.setItems(filteredSettlements);
        if (historyTable != null) historyTable.setItems(filteredHistory);
    }


    // ═══════════════════════════════════════════════════════════════
    // Column helpers
    // ═══════════════════════════════════════════════════════════════

    @FunctionalInterface interface StrFn<T> { String apply(T t); }

    private TableColumn<IssueRecord,  String> col(String n, StrFn<IssueRecord>  f, double w) { return makeCol(n,f,w); }
    private TableColumn<BorrowRequest,String> col2(String n, StrFn<BorrowRequest> f, double w) { return makeCol(n,f,w); }
    private TableColumn<IssueRecord,  String> colIR(String n, StrFn<IssueRecord> f, double w) { return makeCol(n,f,w); }

    private TableColumn<IssueRecord, String> colC(String n, StrFn<IssueRecord> f, double w) { return makeColCenter(n,f,w); }
    private TableColumn<BorrowRequest, String> col2C(String n, StrFn<BorrowRequest> f, double w) { return makeColCenter(n,f,w); }
    private TableColumn<IssueRecord, String> colIRC(String n, StrFn<IssueRecord> f, double w) { return makeColCenter(n,f,w); }

    private static <T> TableColumn<T, String> makeCol(String name, StrFn<T> fn, double w) {
        TableColumn<T, String> c = new TableColumn<>(name);
        c.setSortable(false);
        c.getStyleClass().add("col-left");
        c.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(fn.apply(d.getValue())));
        c.setPrefWidth(w);
        c.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("col-left"); }
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty ? null : s);
            }
        });
        return c;
    }

    private static <T> TableColumn<T, String> makeColCenter(String name, StrFn<T> fn, double w) {
        TableColumn<T, String> c = new TableColumn<>(name);
        c.setSortable(false);
        c.getStyleClass().add("col-center");
        c.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(fn.apply(d.getValue())));
        c.setPrefWidth(w);
        c.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("col-center"); }
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty ? null : s);
            }
        });
        return c;
    }

    // ═══════════════════════════════════════════════════════════════
    // Style helpers
    // ═══════════════════════════════════════════════════════════════

    private static Button actionIconBtn(String iconPath, String tooltip, String color, AppTheme.ButtonStyle style) {
        Button b = AppTheme.createIconButton(iconPath, tooltip, style);
        // Custom styling for table actions to keep them compact but vibrant
        String baseStyle = "-fx-background-color:" + color + "; -fx-background-radius:8px; " +
                "-fx-cursor:hand; -fx-padding:5; -fx-min-width:28px; -fx-pref-width:28px; " +
                "-fx-max-width:28px; -fx-min-height:28px; -fx-pref-height:28px; -fx-max-height:28px;" +
                "-fx-translate-x:2;";
        b.setStyle(baseStyle);
        var icon = (javafx.scene.shape.SVGPath) b.getGraphic();
        if (icon != null) icon.setStyle("-fx-fill: white;");

        b.setOnMouseEntered(e -> b.setOpacity(0.85));
        b.setOnMouseExited(e -> b.setOpacity(1.0));
        return b;
    }

    private static Tab tab(String title, String iconPath, Node content) {
        Tab tab = new Tab(title, content);
        tab.setGraphic(AppTheme.createIcon(iconPath, 14));
        return tab;
    }
    private static Label fieldLabel(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-size:13px; -fx-font-weight:600; -fx-text-fill:" +
                (AppTheme.darkMode ? "#CBD5E1" : "#374151") + ";");
        return l;
    }
    private static Label styledLabel(String t, int size, String color, boolean bold) {
        Label l = new Label(t);
        l.setStyle("-fx-font-size:" + size + "px; -fx-text-fill:" + color + ";" +
                (bold ? "-fx-font-weight:700;" : ""));
        l.setWrapText(true);
        return l;
    }
    private static String inputStyle() {
        if (AppTheme.darkMode) {
            return "-fx-background-color:#1E293B; -fx-border-color:#334155; " +
                    "-fx-border-width:1.5; -fx-border-radius:10px; -fx-background-radius:10px; " +
                    "-fx-padding:10 14; -fx-font-size:14px; -fx-text-fill:#E2E8F0;";
        }
        return "-fx-background-color:#F9FAFB; -fx-border-color:#D1D5DB; " +
                "-fx-border-width:1.5; -fx-border-radius:10px; -fx-background-radius:10px; " +
                "-fx-padding:10 14; -fx-font-size:14px;";
    }

    private static String listSurfaceStyle() {
        return "-fx-background-color:" + (AppTheme.darkMode ? "#1E293B" : "white") + "; " +
                "-fx-border-color:" + (AppTheme.darkMode ? "#334155" : "#E2E8F0") + "; " +
                "-fx-border-radius:8px; -fx-background-radius:8px;";
    }

    private void showLongTextDialog(String title, String value) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        AppTheme.applyTheme(alert.getDialogPane());

        TextArea textArea = new TextArea(value);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(10);
        textArea.setPrefColumnCount(38);
        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
    }

    private static String pageBackground() {
        return AppTheme.darkMode ? "#0F172A" : "#F1F5F9";
    }

    private static String textPrimary() {
        return AppTheme.darkMode ? "#F8FAFC" : "#0F172A";
    }

    private static String textMuted() {
        return AppTheme.darkMode ? "#94A3B8" : "#64748B";
    }

    private static String overdueBannerBackground() {
        return AppTheme.darkMode ? "rgba(127,29,29,0.28)" : "#FEF2F2";
    }

    private static String overdueBannerBorder() {
        return AppTheme.darkMode ? "#7F1D1D" : "#FECACA";
    }

    private static String overdueIconSurface() {
        return AppTheme.darkMode ? "rgba(248,113,113,0.16)" : "#FCA5A522";
    }

    private static String overdueBannerTitle() {
        return AppTheme.darkMode ? "#FECACA" : "#991B1B";
    }

    private static String overdueBannerText() {
        return AppTheme.darkMode ? "#FCA5A5" : "#B91C1C";
    }

    private static String valueOrPlaceholder(String value) {
        return value == null || value.isBlank() ? "(not provided)" : value;
    }
    public void refresh() {
        if (issuesTable != null) issuesTable.refresh();
        if (requestsTable != null) requestsTable.refresh();
        if (overdueTable != null) overdueTable.refresh();
        if (userOverdueTable != null) userOverdueTable.refresh();
        if (settlementsTable != null) settlementsTable.refresh();
        if (historyTable != null) historyTable.refresh();
        updateRemindAllBtnState();
    }

    private void updateRemindAllBtnState() {
        if (remindAllBtn != null) {
            boolean systemCanEmail = com.example.services.AppConfigurationService.getConfiguration().isEmailConfigured();
            boolean hasOverdue = !filteredOverdue.isEmpty();
            remindAllBtn.setDisable(!systemCanEmail || !hasOverdue);
        }
    }


    public void selectTab(int index) {
        TabPane tp = findTabPane(this);
        if (tp != null && index >= 0 && index < tp.getTabs().size()) {
            tp.getSelectionModel().select(index);
        }
    }

    private TabPane findTabPane(Node root) {
        if (root instanceof TabPane tp) return tp;
        if (root instanceof ScrollPane sp && sp.getContent() != null) {
            TabPane nested = findTabPane(sp.getContent());
            if (nested != null) return nested;
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                TabPane nested = findTabPane(child);
                if (nested != null) return nested;
            }
        }
        return null;
    }
}
