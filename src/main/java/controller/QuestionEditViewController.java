package main.java.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.stream.Collectors;

import com.jfoenix.controls.JFXTextField;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import main.java.base.Alternative;
import main.java.base.Category;
import main.java.base.CategoryFamily;
import main.java.base.Question;
import main.java.base.Vote;
import main.java.base.Voter;
import main.java.base.ordering.Ballot;
import main.java.io.reader.BallotReader;
import main.java.io.reader.CategoryReader;
import main.java.util.DialogBuilder;
import main.java.util.EditCell;
import main.java.viewModel.QuestionEditVoteViewModel;

public class QuestionEditViewController {

    @FXML
    private StackPane mainPane;
    @FXML
    private JFXTextField descriptionTextField;
    @FXML
    private ListView<Alternative> alternativeListView;
    @FXML
    private TableView<QuestionEditVoteViewModel> votesTableView;
    @FXML
    private TableColumn<QuestionEditVoteViewModel, String> voterColumn;
    @FXML
    private TableColumn<QuestionEditVoteViewModel, String> ballotColumn;
    @FXML
    private TableColumn<QuestionEditVoteViewModel, String> categoryColumn;
    @FXML
    private ComboBox<Voter> voterComboBox;
    
    private Question questionCopy;
    private Map<String, Alternative> nametoAlternative;
    private Vector<CategoryFamily> categories;
    private Vector<Voter> voters; 
    
    private ObservableList<QuestionEditVoteViewModel> votesTableList = FXCollections.observableArrayList();
    
    private HashMap<String, BooleanProperty> selectedAlternativeMap = new HashMap<String, BooleanProperty>();
    
	public Question getEditedQuestion() {
		return this.questionCopy;
	}
	
	public Vector<CategoryFamily> getEditedCategories () {
		return this.categories;
	}
	
	public void setupView(Question question, Vector<Alternative> alternatives, Vector<CategoryFamily> categories, Vector<Voter> voters) {
		this.questionCopy = new Question(question);
		this.nametoAlternative = alternatives.stream().collect(Collectors.toMap(Alternative::getName, a -> a));
		this.categories = categories;
		this.voters = voters;
		setQuestionDescriptionField(this.questionCopy);
		setAlternativesListView(alternatives, this.questionCopy.getAlternatives());
		setVoteTableView();
		setVoterComboBox();
	}

	private void setQuestionDescriptionField(Question question) {
		descriptionTextField.setText(question.getDescription());
		descriptionTextField.textProperty().addListener((observable, oldValue, newValue) -> {
			updateQuestionDescription(newValue);
		});
	}

	private void setAlternativesListView(Vector<Alternative> alternatives, Vector<Alternative> selectedAlternatives) {
		for (Alternative a : alternatives)
			selectedAlternativeMap.put(a.getName(), new SimpleBooleanProperty(false));
		for (Alternative a : selectedAlternatives)
			selectedAlternativeMap.put(a.getName(), new SimpleBooleanProperty(true));
		alternativeListView.setCellFactory(CheckBoxListCell.forListView(item -> {
			BooleanProperty cb = this.selectedAlternativeMap.get(item.getName());
			cb.addListener((obs,wasSelected,nowSelected) -> {
				if (nowSelected)
					addAlternativeToQuestion(item.getName());
				else
					removeAlternativeFromQuestion(item.getName());
			});
			return cb;
		}));
		alternativeListView.setItems(FXCollections.observableArrayList(alternatives).sorted((a,b) -> a.getName().compareToIgnoreCase(b.getName())));
		alternativeListView.prefHeightProperty().bind(Bindings.size(alternativeListView.getItems()).multiply(28));
	}

	private void setVoteTableView() {
		updateVoteTableViewItems();
		votesTableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		votesTableView.setItems(votesTableList);
		voterColumn.setCellValueFactory(cellData -> {
    		return cellData.getValue().getVoter();
    	});
		ballotColumn.setCellValueFactory(cellData -> {
    		return cellData.getValue().getRanking();
    	});
		categoryColumn.setCellValueFactory(cellData -> {
    		return cellData.getValue().getCategories();
    	});
		votesTableView.getSortOrder().add(voterColumn);
		votesTableView.setOnMouseClicked(event -> {
			if(event.getButton().equals(MouseButton.PRIMARY)){
	            if(event.getClickCount() == 2 && votesTableView.getSelectionModel().getSelectedItem() != null) {
					showVoteEditDialog(votesTableView.getSelectionModel().getSelectedItem().getVote());
	            }
	        }
		});
	}

	private void showVoteEditDialog(Vote vote) {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/main/fxml/VoteEditView.fxml"));
			StackPane content = loader.load();
			VoteEditViewController controller = (VoteEditViewController) loader.getController();
			controller.setupView(vote, categories);
			DialogBuilder.showConfirmCancelDialog(content, this.mainPane, new EventHandler<ActionEvent>() {
			    @Override public void handle(ActionEvent e) {
			    	if (controller.getBallot() != null && controller.getCategories() != null) {
			    		vote.setRanking(controller.getBallot());
			    		vote.setCategories(controller.getCategories());
			    		updateVoteTableViewItems();
			    	}
			    }
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void setVoterComboBox() {
		this.voterComboBox.setItems(FXCollections.observableArrayList(
				this.voters.stream().filter(vr -> 
				!this.questionCopy.getVotes().stream().anyMatch(vt -> 
				vt.getVoter().equals(vr))).collect(Collectors.toList())));
		this.voterComboBox.getSelectionModel().selectFirst();
	}

	private void updateVoteTableViewItems() {
		votesTableList.setAll(this.questionCopy.getVotes().stream().
				map(QuestionEditVoteViewModel::new).
				collect(Collectors.toList()));
		votesTableView.sort();
	}

	private void updateQuestionDescription(String description) {
		this.questionCopy.setDescription(description);
	}

	private void addAlternativeToQuestion(String alternative) {
		this.questionCopy.addAlternative(this.nametoAlternative.get(alternative));
		updateVoteTableViewItems();
	}
	
	private void removeAlternativeFromQuestion(String alternative) {
		this.questionCopy.removeAlternative(this.nametoAlternative.get(alternative));
		updateVoteTableViewItems();
	}

	private void updateVoteFromBallotString(String ballotString, Vote vote) {
		Ballot updatedBallot = BallotReader.fromString(ballotString, this.questionCopy.getAlternatives(), this.nametoAlternative);
		if (updatedBallot != null)
			vote.setRanking(updatedBallot);
		updateVoteTableViewItems();
	}

	private void updateVoteFromCategoryString(String categoryString, Vote vote) {
		Map<CategoryFamily, Category> updatedCategories = CategoryReader.updateAndGetFromString(this.categories, categoryString);
		if (updatedCategories != null)
			vote.setCategories(updatedCategories);
		updateVoteTableViewItems();
	}
	
	@FXML
    void addVote(MouseEvent event) {
		Voter voter = this.voterComboBox.getSelectionModel().getSelectedItem();
		if (voter == null)
			return;
		Map<Alternative, Integer> rankForElement = new HashMap<Alternative, Integer>();
		this.questionCopy.getAlternatives().forEach(a -> rankForElement.put(a, 1));
		Vote vote = new Vote(voter, new Ballot(rankForElement), new HashMap<CategoryFamily, Category>());
		this.questionCopy.addVote(vote);
		this.votesTableList.add(new QuestionEditVoteViewModel(vote));
		this.voterComboBox.getItems().remove(voter);
    	this.voterComboBox.getSelectionModel().selectFirst();
    }

    @FXML
    void removeVote(MouseEvent event) {
    	QuestionEditVoteViewModel item = this.votesTableView.getSelectionModel().getSelectedItem();
    	if (item == null)
    		return;
    	Vote vote = item.getVote();
    	this.questionCopy.removeVote(vote);
    	this.votesTableList.remove(item);
    	this.voterComboBox.getItems().add(vote.getVoter());
    	this.voterComboBox.getSelectionModel().selectFirst();
    }

}