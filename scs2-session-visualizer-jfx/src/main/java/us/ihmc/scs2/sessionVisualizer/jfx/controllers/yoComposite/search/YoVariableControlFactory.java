package us.ihmc.scs2.sessionVisualizer.jfx.controllers.yoComposite.search;

import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.converter.DoubleStringConverter;
import us.ihmc.javaFXExtensions.control.LongSpinnerValueFactory;
import us.ihmc.scs2.sessionVisualizer.jfx.properties.YoBooleanProperty;
import us.ihmc.scs2.sessionVisualizer.jfx.properties.YoDoubleProperty;
import us.ihmc.scs2.sessionVisualizer.jfx.properties.YoEnumAsStringProperty;
import us.ihmc.scs2.sessionVisualizer.jfx.properties.YoIntegerProperty;
import us.ihmc.scs2.sessionVisualizer.jfx.properties.YoLongProperty;
import us.ihmc.scs2.sessionVisualizer.jfx.properties.YoVariableProperty;
import us.ihmc.scs2.sessionVisualizer.jfx.tools.ScientificDoubleStringConverter;
import us.ihmc.scs2.sharedMemory.LinkedYoRegistry;
import us.ihmc.yoVariables.variable.YoBoolean;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.yoVariables.variable.YoEnum;
import us.ihmc.yoVariables.variable.YoInteger;
import us.ihmc.yoVariables.variable.YoLong;
import us.ihmc.yoVariables.variable.YoVariable;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public final class YoVariableControlFactory
{
   private static final double GRAPHIC_PREF_WIDTH = 110.0;

   private YoVariableControlFactory()
   {
   }

   public static List<Region> createYoVariableControls(Collection<YoVariable> yoVariables,
                                                      Property<Integer> numberPrecision,
                                                      LinkedYoRegistry linkedRegistry,
                                                      Object owner,
                                                      ReadOnlyBooleanProperty disabledProperty,
                                                      List<YoVariableProperty<?, ?>> propsOut)
   {
      return yoVariables.stream()
                        .map(v -> createYoVariableControl(v, numberPrecision, linkedRegistry, owner, disabledProperty, propsOut))
                        .collect(Collectors.toList());
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   public static Region createYoVariableControl(YoVariable yoVariable,
                                                Property<Integer> numberPrecision,
                                                LinkedYoRegistry linkedRegistry,
                                                Object owner,
                                                ReadOnlyBooleanProperty disabledProperty,
                                                List<YoVariableProperty<?, ?>> propsOut)
   {
      if (yoVariable instanceof YoDouble)
         return createYoDoubleControl((YoDouble) yoVariable, numberPrecision, linkedRegistry, owner, disabledProperty, propsOut);
      if (yoVariable instanceof YoBoolean)
         return createYoBooleanControl((YoBoolean) yoVariable, linkedRegistry, owner, disabledProperty, propsOut);
      if (yoVariable instanceof YoLong)
         return createYoLongControl((YoLong) yoVariable, linkedRegistry, owner, disabledProperty, propsOut);
      if (yoVariable instanceof YoInteger)
         return createYoIntegerControl((YoInteger) yoVariable, linkedRegistry, owner, disabledProperty, propsOut);
      if (yoVariable instanceof YoEnum)
         return createYoEnumControl((YoEnum) yoVariable, linkedRegistry, owner, disabledProperty, propsOut);
      throw new UnsupportedOperationException("Unhandled YoVariable type: " + yoVariable.getClass().getSimpleName());
   }

   public static Control createYoDoubleControl(YoDouble yoDouble,
                                               Property<Integer> numberPrecision,
                                               LinkedYoRegistry linkedRegistry,
                                               Object owner,
                                               ReadOnlyBooleanProperty disabledProperty,
                                               List<YoVariableProperty<?, ?>> propsOut)
   {
      YoDoubleProperty yoDoubleProperty = new YoDoubleProperty(yoDouble, owner);
      yoDoubleProperty.setLinkedBuffer(disabledProperty.get() ? null : linkedRegistry.linkYoVariable(yoDouble, yoDoubleProperty));
      disabledProperty.addListener((o, oldValue, newValue) -> yoDoubleProperty.setLinkedBuffer(newValue ?
                                                                                                       null :
                                                                                                       linkedRegistry.linkYoVariable(yoDouble,
                                                                                                                                     yoDoubleProperty)));
      propsOut.add(yoDoubleProperty);

      YoDoubleSpinnerValueFactory valueFactory = new YoDoubleSpinnerValueFactory(yoDoubleProperty.getValue());
      DoubleStringConverter rawDoubleStringConverter = new DoubleStringConverter();
      ScientificDoubleStringConverter scientificDoubleStringConverter = new ScientificDoubleStringConverter(numberPrecision);
      valueFactory.setConverter(scientificDoubleStringConverter);
      Spinner<Double> spinner = new Spinner<>(valueFactory);
      spinner.setPrefWidth(GRAPHIC_PREF_WIDTH);
      spinner.setEditable(true);
      spinner.focusedProperty().addListener((o, oldValue, newValue) ->
                                            {
                                               valueFactory.setConverter(newValue ? rawDoubleStringConverter : scientificDoubleStringConverter);
                                               spinner.getEditor().setText(valueFactory.getConverter().toString(valueFactory.getValue()));
                                            });
      yoDoubleProperty.bindDoubleProperty(spinner.getValueFactory().valueProperty());

      Tooltip tooltip = new Tooltip();
      tooltip.textProperty().bind(spinner.valueProperty().asString());
      spinner.setTooltip(tooltip);

      return spinner;
   }

   public static Region createYoBooleanControl(YoBoolean yoBoolean,
                                               LinkedYoRegistry linkedRegistry,
                                               Object owner,
                                               ReadOnlyBooleanProperty disabledProperty,
                                               List<YoVariableProperty<?, ?>> propsOut)
   {
      YoBooleanProperty yoBooleanProperty = new YoBooleanProperty(yoBoolean, owner);
      yoBooleanProperty.setLinkedBuffer(disabledProperty.get() ? null : linkedRegistry.linkYoVariable(yoBoolean, yoBooleanProperty));
      disabledProperty.addListener((o, oldValue, newValue) -> yoBooleanProperty.setLinkedBuffer(newValue ?
                                                                                                        null :
                                                                                                        linkedRegistry.linkYoVariable(yoBoolean,
                                                                                                                                      yoBooleanProperty)));
      propsOut.add(yoBooleanProperty);

      CheckBox checkBox = new CheckBox();
      HBox root = new HBox(checkBox);
      root.setPrefWidth(GRAPHIC_PREF_WIDTH);
      root.alignmentProperty().set(Pos.CENTER_LEFT);
      checkBox.setSelected(yoBooleanProperty.getValue());
      yoBooleanProperty.bindBooleanProperty(checkBox.selectedProperty());

      return root;
   }

   public static Control createYoLongControl(YoLong yoLong,
                                             LinkedYoRegistry linkedRegistry,
                                             Object owner,
                                             ReadOnlyBooleanProperty disabledProperty,
                                             List<YoVariableProperty<?, ?>> propsOut)
   {
      YoLongProperty yoLongProperty = new YoLongProperty(yoLong, owner);
      yoLongProperty.setLinkedBuffer(disabledProperty.get() ? null : linkedRegistry.linkYoVariable(yoLong, yoLongProperty));
      disabledProperty.addListener((o, oldValue, newValue) -> yoLongProperty.setLinkedBuffer(newValue ?
                                                                                                     null :
                                                                                                     linkedRegistry.linkYoVariable(yoLong, yoLongProperty)));
      propsOut.add(yoLongProperty);

      LongSpinnerValueFactory valueFactory = new LongSpinnerValueFactory(Long.MIN_VALUE, Long.MAX_VALUE, yoLongProperty.getValue(), 1L);
      Spinner<Long> spinner = new Spinner<>(valueFactory);
      spinner.setPrefWidth(GRAPHIC_PREF_WIDTH);
      spinner.setEditable(true);
      spinner.focusedProperty().addListener((o, oldValue, newValue) ->
                                            {
                                               if (!newValue)
                                                  spinner.getEditor().setText(valueFactory.getConverter().toString(valueFactory.getValue()));
                                            });
      yoLongProperty.bindLongProperty(spinner.getValueFactory().valueProperty());

      Tooltip tooltip = new Tooltip();
      tooltip.textProperty().bind(spinner.valueProperty().asString());
      spinner.setTooltip(tooltip);

      return spinner;
   }

   public static Control createYoIntegerControl(YoInteger yoInteger,
                                                LinkedYoRegistry linkedRegistry,
                                                Object owner,
                                                ReadOnlyBooleanProperty disabledProperty,
                                                List<YoVariableProperty<?, ?>> propsOut)
   {
      YoIntegerProperty yoIntegerProperty = new YoIntegerProperty(yoInteger, owner);
      yoIntegerProperty.setLinkedBuffer(disabledProperty.get() ? null : linkedRegistry.linkYoVariable(yoInteger, yoIntegerProperty));
      disabledProperty.addListener((o, oldValue, newValue) -> yoIntegerProperty.setLinkedBuffer(newValue ?
                                                                                                        null :
                                                                                                        linkedRegistry.linkYoVariable(yoInteger,
                                                                                                                                      yoIntegerProperty)));
      propsOut.add(yoIntegerProperty);

      IntegerSpinnerValueFactory valueFactory = new IntegerSpinnerValueFactory(Integer.MIN_VALUE, Integer.MAX_VALUE, yoIntegerProperty.getValue(), 1);
      Spinner<Integer> spinner = new Spinner<>(valueFactory);
      spinner.setPrefWidth(GRAPHIC_PREF_WIDTH);
      spinner.setEditable(true);
      spinner.focusedProperty().addListener((o, oldValue, newValue) ->
                                            {
                                               if (!newValue)
                                                  spinner.getEditor().setText(valueFactory.getConverter().toString(valueFactory.getValue()));
                                            });
      yoIntegerProperty.bindIntegerProperty(spinner.getValueFactory().valueProperty());

      Tooltip tooltip = new Tooltip();
      tooltip.textProperty().bind(spinner.valueProperty().asString());
      spinner.setTooltip(tooltip);

      return spinner;
   }

   public static <E extends Enum<E>> Control createYoEnumControl(YoEnum<E> yoEnum,
                                                                 LinkedYoRegistry linkedRegistry,
                                                                 Object owner,
                                                                 ReadOnlyBooleanProperty disabledProperty,
                                                                 List<YoVariableProperty<?, ?>> propsOut)
   {
      YoEnumAsStringProperty<E> yoEnumProperty = new YoEnumAsStringProperty<>(yoEnum, owner);
      yoEnumProperty.setLinkedBuffer(disabledProperty.get() ? null : linkedRegistry.linkYoVariable(yoEnum, yoEnumProperty));
      disabledProperty.addListener((o, oldValue, newValue) -> yoEnumProperty.setLinkedBuffer(newValue ?
                                                                                                     null :
                                                                                                     linkedRegistry.linkYoVariable(yoEnum, yoEnumProperty)));
      propsOut.add(yoEnumProperty);

      ObservableList<String> items = FXCollections.observableArrayList(yoEnumProperty.getYoVariable().getEnumValuesAsString());
      if (yoEnumProperty.getYoVariable().isNullAllowed())
         items.add(YoEnum.NULL_VALUE_STRING);
      ComboBox<String> comboBox = new ComboBox<>(items);
      comboBox.setValue(yoEnumProperty.getValue());
      comboBox.setPrefWidth(GRAPHIC_PREF_WIDTH);
      comboBox.setEditable(false);
      yoEnumProperty.bindStringProperty(comboBox.valueProperty());

      Tooltip tooltip = new Tooltip();
      tooltip.textProperty().bind(comboBox.valueProperty());
      comboBox.setTooltip(tooltip);

      return comboBox;
   }
}
