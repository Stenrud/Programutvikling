package controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

/**
 * Created by remibengtpettersen on 12.02.2016.
 */
public class FrontPageController {

    MasterController master;

    @FXML
    Button editorBtn, startBtn;


    /**
     * To pass reference between controllers
     * @param master
     */
    public void setMaster(MasterController master){

        this.master = master;
    }

    /**
     *
     * @param actionEvent
     */
    public void startGame(ActionEvent actionEvent) {
    }

    /**
     *
     * @param actionEvent
     */
    public void startEditor(ActionEvent actionEvent) {
    }



}
