package us.ihmc.scs2.sessionVisualizer.jfx.controllers.yoComposite.entry;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.*;
import us.ihmc.log.LogTools;
import us.ihmc.messager.MessagerAPIFactory.Topic;
import us.ihmc.messager.javafx.JavaFXMessager;
import us.ihmc.scs2.definition.yoEntry.YoEntryDefinition;
import us.ihmc.scs2.definition.yoEntry.YoEntryListDefinition;
import us.ihmc.scs2.sessionVisualizer.jfx.SessionVisualizerTopics;
import us.ihmc.scs2.sessionVisualizer.jfx.YoNameDisplay;
import us.ihmc.scs2.sessionVisualizer.jfx.controllers.yoComposite.search.YoCompositeListCell;
import us.ihmc.scs2.sessionVisualizer.jfx.managers.SessionVisualizerToolkit;
import us.ihmc.scs2.sessionVisualizer.jfx.managers.YoCompositeSearchManager;
import us.ihmc.scs2.sessionVisualizer.jfx.managers.YoManager;
import us.ihmc.scs2.sessionVisualizer.jfx.tools.DragAndDropTools;
import us.ihmc.scs2.sessionVisualizer.jfx.tools.MenuTools;
import us.ihmc.scs2.sessionVisualizer.jfx.yoComposite.YoComposite;
import us.ihmc.scs2.sessionVisualizer.jfx.yoComposite.YoCompositeCollection;
import us.ihmc.scs2.sessionVisualizer.jfx.yoComposite.YoCompositePattern;
import us.ihmc.yoVariables.variable.YoVariable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static us.ihmc.scs2.sessionVisualizer.jfx.tools.ListViewTools.removeMenuItemFactory;

public class YoEntryListViewController
{
   @FXML
   private ListView<YoComposite> yoEntryListView;

   private final StringProperty nameProperty = new SimpleStringProperty(this, "name", null);
   private YoManager yoManager;
   private YoCompositeSearchManager yoCompositeSearchManager;
   private JavaFXMessager messager;
   private Topic<List<String>> yoCompositeSelectedTopic;
   private AtomicReference<List<String>> yoCompositeSelected;

   public void initialize(SessionVisualizerToolkit toolkit)
   {
      messager = toolkit.getMessager();
      SessionVisualizerTopics topics = toolkit.getTopics();
      Property<Integer> numberPrecision = messager.createPropertyInput(topics.getControlsNumberPrecision(), 3);
      Property<YoNameDisplay> yoVariableNameDisplay = messager.createPropertyInput(topics.getYoVariableNameDisplay());

      yoManager = toolkit.getYoManager();
      yoCompositeSearchManager = toolkit.getYoCompositeSearchManager();
      yoEntryListView.setCellFactory(param ->
      {
         YoCompositeListCell cell = new YoCompositeListCell(toolkit.getYoManager(), yoVariableNameDisplay, numberPrecision, param);
         cell.setOnDragOver(event ->
         {
            if (isWithinListReorder(event) && !cell.isEmpty())
            {
               event.acceptTransferModes(TransferMode.COPY);
               showCellDropIndicator(cell, event.getY() < cell.getHeight() / 2.0);
               event.consume();
            }
         });
         cell.setOnDragExited(event ->
         {
            clearCellDropIndicator(cell);
            event.consume();
         });
         cell.setOnDragDropped(event ->
         {
            if (isWithinListReorder(event) && !cell.isEmpty())
            {
               int targetIndex = yoEntryListView.getItems().indexOf(cell.getItem());
               boolean insertBefore = event.getY() < cell.getHeight() / 2.0;
               reorderItems(new ArrayList<>(yoEntryListView.getSelectionModel().getSelectedIndices()), targetIndex, insertBefore);
               event.setDropCompleted(true);
               event.consume();
            }
         });
         return cell;
      });
      yoEntryListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
      MenuTools.setupContextMenu(yoEntryListView, removeMenuItemFactory(true));

      yoEntryListView.setOnDragDetected(this::handleDragDetected);
      yoEntryListView.setOnDragEntered(this::handleDragEntered);
      yoEntryListView.setOnDragExited(this::handleDragExited);
      yoEntryListView.setOnDragOver(this::handleDragOver);
      yoEntryListView.setOnDragDropped(this::handleDragDropped);
      yoEntryListView.setOnMouseReleased(this::handleOnMouseReleased);

      yoCompositeSelectedTopic = topics.getYoCompositeSelected();
      yoCompositeSelected = messager.createInput(yoCompositeSelectedTopic);

      yoEntryListView.getSelectionModel().selectedItemProperty().addListener((o, oldValue, newValue) ->
                                                                             {
                                                                                if (newValue != null)
                                                                                   messager.submitMessage(yoCompositeSelectedTopic,
                                                                                                          Arrays.asList(newValue.getPattern().getType(),
                                                                                                                        newValue.getFullname()));
                                                                             });
   }

   public void setInput(YoEntryListDefinition input)
   {
      if (input.getName() != null)
         nameProperty.set(input.getName());

      yoEntryListView.getItems().clear();

      addYoEntries(input.getYoEntries());
   }

   public void addYoEntries(List<YoEntryDefinition> yoEntries)
   {
      if (yoEntries == null)
         return;

      for (YoEntryDefinition entry : yoEntries)
      {
         String type = entry.getCompositeType();
         String fullname = entry.getCompositeFullname();

         YoCompositeCollection collection;

         if (type == null)
            collection = yoCompositeSearchManager.getYoVariableCollection();
         else
            collection = yoCompositeSearchManager.getCollectionFromType(type);

         if (collection == null)
         {
            LogTools.warn("Could not find composite type: " + type);
            continue;
         }

         YoComposite yoComposite = collection.getYoCompositeFromFullname(fullname);

         if (yoComposite != null && !yoEntryListView.getItems().contains(yoComposite))
         {
            yoEntryListView.getItems().add(yoComposite);
            continue;
         }

         yoComposite = collection.getYoCompositeFromUniqueName(fullname);
         if (yoComposite != null && !yoEntryListView.getItems().contains(yoComposite))
         {
            yoEntryListView.getItems().add(yoComposite);
            continue;
         }

         YoCompositePattern pattern = collection.getPattern();

         if (pattern.getComponentIdentifiers() != null && pattern.getComponentIdentifiers().length == 1)
         {
            YoVariable variable = yoManager.getRootRegistry().findVariable(fullname);
            if (variable != null)
            {
               yoComposite = collection.getYoCompositeFromFullname(variable.getFullNameString());
               if (yoComposite != null && !yoEntryListView.getItems().contains(yoComposite))
               {
                  yoEntryListView.getItems().add(yoComposite);
                  continue;
               }
            }
         }
         LogTools.warn("Could not find composite: " + fullname);
      }
   }

   public YoEntryListDefinition toYoEntryListDefinition()
   {
      YoEntryListDefinition definition = new YoEntryListDefinition();
      definition.setName(nameProperty.get());
      definition.setYoEntries(new ArrayList<>());

      for (YoComposite entry : yoEntryListView.getItems())
      {
         YoEntryDefinition yoEntryDefinition = new YoEntryDefinition();
         yoEntryDefinition.setCompositeType(entry.getPattern().getType());
         yoEntryDefinition.setCompositeFullname(entry.getFullname());
         definition.getYoEntries().add(yoEntryDefinition);
      }
      return definition;
   }

   public StringProperty nameProperty()
   {
      return nameProperty;
   }

   public void clear()
   {
      yoEntryListView.getItems().clear();
   }

   public boolean isEmpty()
   {
      return yoEntryListView.getItems().isEmpty();
   }

   public void handleOnMouseReleased(MouseEvent event)
   {
      if (event.getButton() != MouseButton.MIDDLE)
         return;

      if (yoCompositeSelected.get() == null)
         return;

      String type = yoCompositeSelected.get().get(0);
      String fullname = yoCompositeSelected.get().get(1);
      YoComposite yoComposite = yoCompositeSearchManager.getYoComposite(type, fullname);

      if (yoComposite != null && !yoEntryListView.getItems().contains(yoComposite))
      {
         yoEntryListView.getItems().add(yoComposite);
         messager.submitMessage(yoCompositeSelectedTopic, null);
      }
   }

   public void handleDragDetected(MouseEvent event)
   {
      if (!event.isPrimaryButtonDown())
         return;

      List<YoComposite> selectedItems = new ArrayList<>(yoEntryListView.getSelectionModel().getSelectedItems());
      if (selectedItems.isEmpty())
         return;

      Dragboard dragBoard = yoEntryListView.startDragAndDrop(TransferMode.COPY);
      ClipboardContent clipboardContent = new ClipboardContent();
      if (selectedItems.size() == 1)
      {
         YoComposite yoComposite = selectedItems.get(0);
         clipboardContent.put(DragAndDropTools.YO_COMPOSITE_REFERENCE, Arrays.asList(yoComposite.getPattern().getType(), yoComposite.getFullname()));
      }
      else
      {
         List<String> content = new ArrayList<>();
         for (YoComposite yoComposite : selectedItems)
         {
            content.add(yoComposite.getPattern().getType());
            content.add(yoComposite.getFullname());
         }
         clipboardContent.put(DragAndDropTools.YO_COMPOSITE_LIST_REFERENCE, content);
      }
      dragBoard.setContent(clipboardContent);
      event.consume();
   }

   private void handleDragEntered(DragEvent event)
   {
      if (!event.isAccepted() && acceptDragEventForDrop(event))
         setSelectionHighlight(true);
      event.consume();
   }

   public void handleDragExited(DragEvent event)
   {
      if (acceptDragEventForDrop(event))
         setSelectionHighlight(false);
      event.consume();
   }

   public void handleDragOver(DragEvent event)
   {
      if (!event.isAccepted() && acceptDragEventForDrop(event))
         event.acceptTransferModes(TransferMode.ANY);
      event.consume();
   }

   public void handleDragDropped(DragEvent event)
   {
      Dragboard db = event.getDragboard();
      boolean success = false;

      List<YoComposite> yoComposites = DragAndDropTools.retrieveYoCompositesFromDragBoard(db, yoCompositeSearchManager);

      if (yoComposites != null)
      {
         for (YoComposite yoComposite : yoComposites)
         {
            if (yoEntryListView.getItems().contains(yoComposite))
               continue;
            yoEntryListView.getItems().add(yoComposite);
            success = true;
         }
      }

      event.setDropCompleted(success);
      event.consume();
   }

   private boolean acceptDragEventForDrop(DragEvent event)
   {
      if (event.getGestureSource() == yoEntryListView)
         return false;

      Dragboard db = event.getDragboard();
      List<YoComposite> yoComposites = DragAndDropTools.retrieveYoCompositesFromDragBoard(db, yoCompositeSearchManager);
      if (yoComposites == null)
         return false;
      return !yoEntryListView.getItems().containsAll(yoComposites);
   }

   public void setSelectionHighlight(boolean isSelected)
   {
      if (isSelected)
         yoEntryListView.setStyle("-fx-border-color:green; -fx-border-radius:5;");
      else
         yoEntryListView.setStyle("-fx-border-color: null;");
   }

   private boolean isWithinListReorder(DragEvent event)
   {
      return event.getGestureSource() == yoEntryListView;
   }

   private void showCellDropIndicator(ListCell<?> cell, boolean above)
   {
      if (above)
         cell.setStyle("-fx-border-color: #4fc3f7 transparent transparent transparent; -fx-border-width: 2 0 0 0;");
      else
         cell.setStyle("-fx-border-color: transparent transparent #4fc3f7 transparent; -fx-border-width: 0 0 2 0;");
   }

   private void clearCellDropIndicator(ListCell<?> cell)
   {
      cell.setStyle(null);
   }

   private void reorderItems(List<Integer> selectedIndices, int dropIndex, boolean insertBefore)
   {
      ObservableList<YoComposite> items = yoEntryListView.getItems();
      List<YoComposite> itemsToMove = selectedIndices.stream().sorted().map(items::get).collect(Collectors.toList());

      int absoluteDropIndex = insertBefore ? dropIndex : dropIndex + 1;
      long selectedBeforeDrop = selectedIndices.stream().filter(i -> i < absoluteDropIndex).count();
      int adjustedIndex = (int) (absoluteDropIndex - selectedBeforeDrop);

      items.removeAll(itemsToMove);
      items.addAll(Math.min(adjustedIndex, items.size()), itemsToMove);

      MultipleSelectionModel<YoComposite> selectionModel = yoEntryListView.getSelectionModel();
      selectionModel.clearSelection();
      int newStart = Math.min(adjustedIndex, items.size() - itemsToMove.size());
      for (int i = newStart; i < newStart + itemsToMove.size(); i++)
         selectionModel.select(i);
   }
}
