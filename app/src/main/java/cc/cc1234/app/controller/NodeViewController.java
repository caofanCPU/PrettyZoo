package cc.cc1234.app.controller;

import cc.cc1234.app.cache.TreeItemCache;
import cc.cc1234.app.context.ActiveServerContext;
import cc.cc1234.app.facade.PrettyZooFacade;
import cc.cc1234.app.fp.Try;
import cc.cc1234.app.listener.DefaultTreeNodeListener;
import cc.cc1234.app.util.FXMLs;
import cc.cc1234.app.view.cell.ZkNodeTreeCell;
import cc.cc1234.app.view.dialog.Dialog;
import cc.cc1234.app.view.toast.VToast;
import cc.cc1234.app.view.transitions.Transitions;
import cc.cc1234.app.vo.ZkNodeSearchResult;
import cc.cc1234.spi.listener.ServerListener;
import cc.cc1234.spi.node.ZkNode;
import cc.cc1234.spi.util.StringWriter;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class NodeViewController {

    private static final Logger log = LoggerFactory.getLogger(NodeViewController.class);

    @FXML
    private TabPane nodeViewPane;

    @FXML
    private AnchorPane nodeViewLeftPane;

    @FXML
    private TextField searchTextField;

    @FXML
    private ListView<ZkNodeSearchResult> searchResultList;

    @FXML
    private TreeView<ZkNode> zkNodeTreeView;

    @FXML
    private StackPane nodeViewRightPane;

    @FXML
    private Button nodeAddButton;

    @FXML
    private Button nodeDeleteButton;

    @FXML
    private Button disconnectButton;

    @FXML
    private Tab homeTab;

    @FXML
    private Tab terminalTab;

    @FXML
    private Tab fourLetterCommandTab;

    @FXML
    private TextArea fourLetterCommandResponseArea;

    @FXML
    private TextField fourLetterCommandRequestArea;

    @FXML
    private TextArea terminalArea;

    @FXML
    private TextField terminalInput;

    private PrettyZooFacade prettyZooFacade = new PrettyZooFacade();

    private NodeInfoViewController nodeInfoViewController = FXMLs.getController("fxml/NodeInfoView.fxml");

    private NodeAddViewController nodeAddViewController = FXMLs.getController("fxml/NodeAddView.fxml");

    @FXML
    public void initialize() {
        nodeViewPane.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.valueOf("#EEE"), 5, 0.1, 3, 5));

        initSearchResultList();
        initSearchTextField();
        initNodeChangeListener();
        initHomeTab();
        initTerminalArea();
        initFourLetterTab();

        nodeAddButton.setOnMouseClicked(e -> onNodeAdd());
        nodeDeleteButton.setOnMouseClicked(e -> onNodeDelete());
        disconnectButton.setTooltip(new Tooltip("disconnect server"));
        disconnectButton.setOnAction(e -> {
            final String server = ActiveServerContext.get();
            prettyZooFacade.disconnect(server);
            hideAndThen(() -> VToast.info("disconnect " + server + " success"));
        });

    }

    public void show(StackPane parent,
                     String server,
                     ServerListener serverListener) {
        if (server != null) {
            switchServer(server, serverListener);
        }

        if (!parent.getChildren().contains(nodeViewPane)) {
            parent.getChildren().add(nodeViewPane);
            Transitions.zoomInY(nodeViewPane).play();
        }
        terminalTab.setText(server);
    }

    public void hideAndThen(Runnable runnable) {
        final StackPane parent = (StackPane) nodeViewPane.getParent();
        if (parent != null) {
            parent.getChildren().remove(nodeViewPane);
            runnable.run();
        } else {
            runnable.run();
        }
    }

    private void onNodeAdd() {
        final TreeItem<ZkNode> selectedItem = zkNodeTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            nodeAddViewController.show(nodeViewRightPane);
        } else {
            final ZkNode zkNode = selectedItem.getValue();
            nodeAddViewController.show(nodeViewRightPane, zkNode);
        }
    }

    private void onNodeDelete() {
        final TreeItem<ZkNode> selectedItem = zkNodeTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            VToast.error("select node first");
        } else {
            final String path = selectedItem.getValue().getPath();
            Dialog.confirm("删除节点", "该操作将删除 " + path + " 该节点及其对应的子节点，操作不可恢复，请谨慎执行", () -> {
                Try.of(() -> prettyZooFacade.deleteNode(ActiveServerContext.get(), path))
                        .onFailure(exception -> VToast.error("delete failed:" + exception.getMessage()))
                        .onSuccess(t -> VToast.info("delete success"));
            });
        }
    }

    private void initSearchTextField() {
        searchTextField.textProperty().addListener((o, old, cur) -> {
            searchResultList.getItems().clear();
            final List<ZkNodeSearchResult> items = prettyZooFacade.onSearch(searchTextField.getText());
            if (!items.isEmpty()) {
                searchResultList.getItems().addAll(items);
                searchResultList.getSelectionModel().select(0);
                if (!searchResultList.isVisible()) {
                    searchResultList.setVisible(true);
                }
            } else {
                if (searchResultList.isVisible()) {
                    searchResultList.setVisible(false);
                }
            }
        });
    }

    private void initSearchResultList() {
        searchResultList.setCellFactory(callback -> new ListCell<ZkNodeSearchResult>() {
            @Override
            protected void updateItem(ZkNodeSearchResult item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(null);
                    setGraphic(item.getTextFlow());
                    setOnMouseClicked(mouseEvent -> {
                        if (mouseEvent.getClickCount() == 2) {
                            ListCell<ZkNodeSearchResult> clickedRow = (ListCell<ZkNodeSearchResult>) mouseEvent.getSource();
                            zkNodeTreeView.getSelectionModel().select(clickedRow.getItem().getItem());
                            zkNodeTreeView.scrollTo(zkNodeTreeView.getSelectionModel().getSelectedIndex());
                            if (searchResultList.isVisible()) {
                                searchResultList.getItems().clear();
                                searchResultList.setVisible(false);
                            }
                        }
                    });
                }
            }
        });
    }

    private void initNodeChangeListener() {
        zkNodeTreeView.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> {
                    nodeAddViewController.hide();
                    if (newValue != null) {
                        nodeInfoViewController.show(nodeViewRightPane, newValue.getValue());
                    }
                });
    }

    private void switchServer(String host, ServerListener serverListener) {
        try {
            log.debug("begin to switch server to {}", host);
            prettyZooFacade.connect(host, List.of(new DefaultTreeNodeListener()), List.of(serverListener));
        } catch (Exception e) {
            log.error("switch server " + host + " failed: ", e);
            throw new IllegalStateException("connect to " + host + " failed", e);
        }

        zkNodeTreeView.setCellFactory(view -> new ZkNodeTreeCell());
        initRootTreeNode(host);
        ActiveServerContext.set(host);
        prettyZooFacade.syncIfNecessary(host);
        final TreeItem<ZkNode> selectedItem = zkNodeTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            nodeInfoViewController.show(nodeViewRightPane, selectedItem.getValue());
        } else {
            nodeInfoViewController.show(nodeViewRightPane);
        }
        log.debug("switch server {} success", host);
    }

    private void initRootTreeNode(String host) {
        final String root = "/";
        final TreeItemCache treeItemCache = TreeItemCache.getInstance();
        if (!treeItemCache.hasNode(host, root)) {
            final ZkNode zkNode = new ZkNode(root, root);
            zkNode.resetStat();
            final TreeItem<ZkNode> rootTreeItem = new TreeItem<>(zkNode);
            treeItemCache.add(host, root, rootTreeItem);
            zkNodeTreeView.setRoot(rootTreeItem);
        }
    }

    private void initHomeTab() {
        final ImageView imageView = new ImageView("assets/img/tab/home.png");
        imageView.setFitWidth(18);
        imageView.setFitHeight(18);
        homeTab.setGraphic(imageView);
    }

    private void initTerminalArea() {
        final ImageView imageView = new ImageView("assets/img/tab/terminal.png");
        imageView.setFitWidth(18);
        imageView.setFitHeight(18);
        terminalTab.setGraphic(imageView);
        terminalTab.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                prettyZooFacade.startTerminal(ActiveServerContext.get(), new StringWriter() {
                    @Override
                    public void write(String str) throws IOException {
                        terminalArea.appendText(str);
                    }

                    @Override
                    public void write(byte[] bytes) throws IOException {
                        terminalArea.appendText(new String(bytes));
                    }
                });
            }
        });

        terminalArea.setEditable(false);
        terminalArea.setWrapText(true);
        terminalArea.textProperty().addListener((ob, old, newValue) -> terminalArea.setScrollTop(Double.MAX_VALUE));
        terminalInput.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                final String currentServer = ActiveServerContext.get();
                if ("clear".equals(terminalInput.getText())) {
                    terminalInput.clear();
                    terminalArea.clear();
                    terminalArea.appendText(currentServer + "\t$\t" + terminalInput.getText());
                } else {
                    terminalArea.appendText(currentServer + "\t$\t" + terminalInput.getText() + "\r\n");
                    prettyZooFacade.executeCommand(currentServer, terminalInput.getText());
                    terminalInput.clear();
                }
                terminalArea.appendText("\r\n");
            } else if (e.getCode() == KeyCode.TAB) {
                terminalInput.appendText("\t");
            }
        });
    }

    private void initFourLetterTab() {
        final ImageView graphic = new ImageView("assets/img/tab/fourLetter.png");
        graphic.setFitHeight(20);
        graphic.setFitWidth(20);
        fourLetterCommandTab.setGraphic(graphic);
        fourLetterCommandRequestArea.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                String command = fourLetterCommandRequestArea.getText();
                if (command == null || command.trim().equals("") || command.length() != 4) {
                    VToast.error("command is invalid: must be 4 words!");
                } else {
                    fourLetterCommandRequestArea.clear();
                    String currentServer = ActiveServerContext.get();
                    String response = prettyZooFacade.executeFourLetterCommand(currentServer, command);
                    fourLetterCommandResponseArea.clear();
                    fourLetterCommandResponseArea.setText(response);
                }
            }
        });
    }

}
