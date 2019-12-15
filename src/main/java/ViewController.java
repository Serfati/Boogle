import Model.Engine.ShowDictionaryRecord;
import View.AlertMaker;
import View.IView;
import ViewModel.ViewModel;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.stage.*;
import org.apache.commons.lang3.SystemUtils;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.ResourceBundle;

public class ViewController implements IView, Observer, Initializable {

    @FXML
    public ImageView saveIcon;
    public ImageView boogleLogo;
    public ImageView searchTab;
    public ImageView generateIndexIcon;
    public ImageView settings;
    @FXML
    public Label lbl_statusBar;
    public TableView<ShowDictionaryRecord> table_showDic;
    public TableColumn<ShowDictionaryRecord, String> tableCol_term;
    public TableColumn<ShowDictionaryRecord, Number> tableCol_count;

    public MenuItem save_MenuItem;
    public MenuItem load_MenuItem;

    public JFXTextField txtfld_output_location;

    @FXML
    public JFXTextField txtfld_corpus_location;
    public JFXTextField txtfld_stopwords_location;
    @FXML
    public JFXButton btn_corpus_browse;
    public JFXButton btn_show_dictionary;
    public JFXButton btn_generate_index;
    public JFXButton btn_stopwords_browse;
    public JFXButton btn_output_browse;
    public JFXButton btn_startOver;
    public JFXButton btn_load_dictionary;
    public Label lbl_totalTime;
    private ViewModel viewModel;
    @FXML
    private JFXToggleButton checkbox_use_stemming;

    /**
     * constructor of view, connect the view to the viewModel
     *
     * @param viewModel the view model of the MVVM
     */
    public void setViewModel(ViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initImages();
    }

    private void initImages() {
        setBoogleClick();
        setClick(generateIndexIcon);
        setClick(settings);
        setClick(saveIcon);
        setClick(searchTab);
    }

    public void setBoogleClick() {
        boogleLogo.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> openGoogle());
    }

    /* WORKS ONLY ON - Windows OS */
    public void openGoogle() {
        String OS = SystemUtils.OS_NAME;

        if (OS.startsWith("Windows")) {
            try {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE))
                    desktop.browse(new URL("https://www.google.com/search?q=STOP+WITH+THIS+SHIT").toURI());
            } catch(IOException | URISyntaxException e1) {
                System.out.println("fail");
                e1.printStackTrace();
            }
        } else { /* UNIX platform - Ubuntu OS */
            try {
                new ProcessBuilder("x-www-browser", "https://www.google.com/search?q=STOP+WITH+THIS+SHIT").start();
            } catch(IOException e) {
                System.out.println("fail");
                e.printStackTrace();
            }
        }
    }

    /**
     * This function starts the process of parse and index the dictionary
     */
    public void onStartClick() {
        if (txtfld_corpus_location.getText().equals("") || txtfld_output_location.getText().equals(""))// check if the paths are not empty
            AlertMaker.showErrorMessage("Error", "path can not be empty");
        else
            viewModel.onStartClick(txtfld_corpus_location.getText(), txtfld_stopwords_location.getText(), txtfld_output_location.getText(), checkbox_use_stemming.isSelected()); //transfer to the view Model
    }

    private void setClick(ImageView icon) {
        icon.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            help();
            event.consume();
        });
    }

    /**
     * transfers a request to show the dictionary of the current indexing
     */
    public void showDictionaryClick() {
        viewModel.showDictionary();
    }

    /**
     * shows an observable list that contains all the data about the current indexing: Term and TF
     *
     * @param records all the data about the current indexing
     */
    private void showDictionary(ObservableList<ShowDictionaryRecord> records) {
        if (records != null) {
            tableCol_term.setCellValueFactory(cellData -> cellData.getValue().getTermProperty());
            tableCol_count.setCellValueFactory(cellData -> cellData.getValue().getCountProperty());
            table_showDic.setItems(records);
        }
        btn_show_dictionary.setDisable(false);
    }

    public void browseCorpusClick(ActionEvent actionEvent) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Corpus Location");
        directoryChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
        File corpusDir = directoryChooser.showDialog(new Stage());
        if (null != corpusDir)  //directory chosen
            txtfld_corpus_location.setText(corpusDir.getAbsolutePath());
    }

    public void browseOutputClick(ActionEvent actionEvent) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Output Location");
        directoryChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
        File corpusDir = directoryChooser.showDialog(new Stage());
        if (null != corpusDir)  //directory chosen
            txtfld_output_location.setText(corpusDir.getAbsolutePath());
    }

    public void browseStopwordsClick(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Stopwords file Location");
        fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
        File corpusDir = fileChooser.showOpenDialog(new Stage());
        if (null != corpusDir)  //directory chosen
            txtfld_stopwords_location.setText(corpusDir.getAbsolutePath());
    }

    public void exitButton() {
        exitCorrectly();
    }

    public void exitCorrectly() {
        Alert alert = new Alert(Alert.AlertType.NONE);
        ButtonType leaveButton = new ButtonType("Leave", ButtonBar.ButtonData.YES);
        ButtonType stayButton = new ButtonType("Stay", ButtonBar.ButtonData.NO);
        alert.getButtonTypes().setAll(stayButton, leaveButton);
        alert.setContentText("Are you sure you want to exit??");
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == leaveButton)
            Platform.exit();
        else
            alert.close();
    }

    public void help() {
        Stage helpStage = new Stage();
        helpStage.setAlwaysOnTop(true);
        helpStage.setResizable(true);
        helpStage.setTitle("Help Window");

        Parent root = null;
        try {
            root = FXMLLoader.load(getClass().getResource("settings.fxml"));
        } catch(IOException e) {
            e.printStackTrace();
            showAlert();
        }
        helpStage.setTitle("Help");
        assert root != null;
        Scene scene = new Scene(root, 520, 495);
        scene.getStylesheets().add(getClass().getResource("dark-style.css").toExternalForm());
        helpStage.setScene(scene);
        helpStage.initModality(Modality.WINDOW_MODAL);
        helpStage.show();
    }

    public void About() {
        Stage aboutStage = new Stage();
        aboutStage.setAlwaysOnTop(true);
        aboutStage.setResizable(false);

        Parent root = null;
        try {
            root = FXMLLoader.load(getClass().getResource("about.fxml"));
        } catch(IOException e) {
            showAlert();
        }
        aboutStage.setTitle("About us");
        assert root != null;
        Scene scene = new Scene(root, 530, 247);
        aboutStage.setScene(scene);
        aboutStage.initModality(Modality.APPLICATION_MODAL);
        aboutStage.show();
    }

    private void showAlert() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setGraphic(null);
        alert.setTitle("Error Alert");
        alert.setContentText("Exception!");
        alert.show();
    }

    /**
     * a function that gets called when an observer has raised a flag for something that changed
     *
     * @param o   - who changed
     * @param arg - the change
     */
    public void update(Observable o, Object arg) {
        if (o == viewModel) {
            if (arg instanceof String[]) {
                String[] toUpdate = (String[]) arg;
                if (toUpdate[0].equals("Fail")) { // if we received a fail message from the model
                    if (toUpdate[1].equals("could not find one or more dictionaries"))
                        AlertMaker.showErrorMessage(Alert.AlertType.ERROR.name(), toUpdate[1]);
                } else if (toUpdate[0].equals("Successful")) {// if we received a successful message from the model
                    AlertMaker.showSimpleAlert(Alert.AlertType.INFORMATION.name(), toUpdate[1]);
                    if (toUpdate[1].substring(0, toUpdate[1].indexOf(" ")).equals("Dictionary")) {
                        btn_show_dictionary.setDisable(false);
                    }
                }
            } else if (arg instanceof ObservableList) { // a show dictionary operation was finished and can be shown on display
                ObservableList l = (ObservableList) arg;
                if (!l.isEmpty() && l.get(0) instanceof ShowDictionaryRecord)
                    showDictionary((ObservableList<ShowDictionaryRecord>) arg);
            } else if (arg instanceof double[]) { // show the results of the indexing
                //showIndexResults times
                btn_show_dictionary.setDisable(false);
            }
        }
    }

    /**
     * This function deletes all the contents of the destination path
     */
    public void onStartOverClick() {
        if (!txtfld_output_location.getText().equals("")) { // check if the user is sure he wants to delete the whole folder he chose
            ButtonType stay = new ButtonType("Yes", ButtonBar.ButtonData.OK_DONE);
            ButtonType leave = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure?", leave, stay);
            Optional<ButtonType> result = alert.showAndWait();
            if (stay != result.get()) {
                return;
            }
            viewModel.onStartOverClick(txtfld_output_location.getText());
        } else
            AlertMaker.showErrorMessage(Alert.AlertType.ERROR.name(), "destination path is unreachable");
    }

    public void saveDictionary(ActionEvent event) {
        int[] choose = {0};
        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle("Save Dictionary");
        alert.setContentText("Which Dictionary do you want to save?");
        ButtonType okButton = new ButtonType("Current", ButtonBar.ButtonData.YES);
        ButtonType noButton = new ButtonType("Original", ButtonBar.ButtonData.NO);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(okButton, noButton, cancelButton);
        alert.showAndWait().ifPresent(type -> {
            if (type == okButton) //Current
                choose[0] = 1;
            else if (type == noButton)  //Original
                choose[0] = 2;
        });
        if (choose[0] == 0) {
            lbl_statusBar.setText("Save was canceled");
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose a directory to save the Dictionary in");
        File filePath = new File("./dictionaries/");
        if (!filePath.exists())
            filePath.mkdir();
        fileChooser.setInitialDirectory(filePath);
        LocalDateTime myDateObj = LocalDateTime.now();
        DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("dd-MM mm:HH");
        String formattedDate = myDateObj.format(myFormatObj);
        fileChooser.setInitialFileName("myDictionary;"+formattedDate);
        File file = fileChooser.showSaveDialog(new PopupWindow() {
        });

        if (file != null) {
            viewModel.saveDictionary(file);
            lbl_statusBar.setText(choose[0] == 1 ? "Current dictionary saved" : "Original dictionary saved");
        }
        event.consume();
    }

    /**
     * transfers to the view model a load dictionary request
     */
    public void loadDictionary(ActionEvent event) {
        System.out.println("loadFile");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose a dictionary to load");
        File filePath = new File("./dictionaries/");
        if (!filePath.exists())
            filePath.mkdir();
        fileChooser.setInitialDirectory(filePath);

        File file = fileChooser.showOpenDialog(new PopupWindow() {
        });
        if (file != null && file.exists() && !file.isDirectory()) {
            viewModel.loadDictionary(file.getAbsolutePath(), checkbox_use_stemming.isSelected());
            lbl_statusBar.setText("Loaded "+file.getName());

        } else
            AlertMaker.showErrorMessage("Invalid", "Please choose a vaild destination");
        event.consume();
    }

}
