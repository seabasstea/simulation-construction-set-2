package us.ihmc.scs2.session.remote;

import us.ihmc.commons.Conversions;
import us.ihmc.log.LogTools;
import us.ihmc.robotDataLogger.YoVariableClientInterface;
import us.ihmc.robotDataLogger.handshake.LogHandshake;
import us.ihmc.robotDataLogger.handshake.YoVariableHandshakeParser;
import us.ihmc.robotDataLogger.util.DebugRegistry;
import us.ihmc.robotDataLogger.websocket.command.DataServerCommand;
import us.ihmc.scs2.definition.robot.RobotDefinition;
import us.ihmc.scs2.definition.robot.RobotStateDefinition;
import us.ihmc.scs2.definition.robot.urdf.URDFTools;
import us.ihmc.scs2.definition.robot.urdf.items.URDFModel;
import us.ihmc.scs2.definition.terrain.TerrainObjectDefinition;
import us.ihmc.scs2.definition.yoGraphic.YoGraphicDefinition;
import us.ihmc.scs2.session.Session;
import us.ihmc.scs2.session.SessionMode;
import us.ihmc.scs2.session.SessionProperties;
import us.ihmc.scs2.session.tools.RobotModelLoader;
import us.ihmc.scs2.simulation.robot.Robot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RemoteSession extends Session
{
   private static final double MAX_DELAY_MILLI = 200.0;

   private YoVariableClientInterface yoVariableClientInterface;

   private final String sessionName;
   private final List<Robot> robots = new ArrayList<>();
   private final List<RobotDefinition> robotDefinitions = new ArrayList<>();
   private final List<YoGraphicDefinition> yoGraphicDefinitions = new ArrayList<>();
   private final Runnable robotStateUpdater;

   private final AtomicLong serverTimestamp = new AtomicLong(-1);
   private final AtomicLong latestDataTimestamp = new AtomicLong(-1);
   private final LoggerStatusUpdater loggerStatusUpdater = new LoggerStatusUpdater();

   private int bufferRecordTickPeriod = 1;
   private boolean initializeServerUpdateRate = true;

   public RemoteSession(YoVariableClientInterface yoVariableClientInterface,
                        LogHandshake handshake,
                        YoVariableHandshakeParser handshakeParser,
                        DebugRegistry debugRegistry)
   {
      super();

      this.yoVariableClientInterface = yoVariableClientInterface;

      sessionName = yoVariableClientInterface.getServerName();

      rootRegistry.addChild(handshakeParser.getRootRegistry());
      rootRegistry.addChild(debugRegistry.getYoRegistry());
      yoGraphicDefinitions.addAll(handshakeParser.getSCS2YoGraphics());

      // Optional override: if -Dscs2.remote.robotModelFile=<path to .urdf> is set, use that local model
      // instead of the one advertised by the server (e.g. to show current mk1_1 geometry when the server
      // still streams an older model). The streamed joint data still binds by joint name in
      // setupRobotUpdater below, so the override's joint names must match what the server sends.
      RobotDefinition robotDefinition = loadRobotModelOverride();
      if (robotDefinition == null)
         robotDefinition = RobotModelLoader.loadModel(handshake.getModelName(),
                                                      handshake.getModelLoaderClass(),
                                                      handshake.getResourceDirectories(),
                                                      handshake.getModel(),
                                                      handshake.getResourceZip());
      if (robotDefinition != null)
      {
         robotDefinitions.add(robotDefinition);
         Robot robot = new Robot(robotDefinition, getInertialFrame());
         robots.add(robot);
         robotStateUpdater = RobotModelLoader.setupRobotUpdater(robot, handshakeParser, rootRegistry);
      }
      else
      {
         robotStateUpdater = null;
      }

      setSessionMode(SessionMode.RUNNING);
      setSessionDTSeconds(handshakeParser.getDt());
      setSessionModeTask(SessionMode.RUNNING, () ->
      {
         if (!this.yoVariableClientInterface.isConnected())
            setSessionMode(SessionMode.PAUSE);
         /* Do nothing, the client thread calls runTick(). */
      });
      addSessionPropertiesListener(properties ->
                                   {
                                      if (properties.getActiveMode() == SessionMode.RUNNING)
                                         reconnect();
                                      else
                                         disconnect();
                                   });
      setDesiredBufferPublishPeriod(Conversions.secondsToNanoseconds(1.0 / 60.0));
   }

   /**
    * Loads a local robot model to use instead of the one advertised by the log server, when the system
    * property {@code scs2.remote.robotModelFile} points to a {@code .urdf} file. Returns {@code null}
    * (falling back to the server model) when the property is unset, the file is missing/not a URDF, or
    * parsing fails. Meshes are resolved relative to the URDF's own directory.
    */
   private static RobotDefinition loadRobotModelOverride()
   {
      String overridePath = System.getProperty("scs2.remote.robotModelFile");
      if (overridePath == null || overridePath.isBlank())
         return null;

      File robotFile = new File(overridePath);
      if (!robotFile.isFile())
      {
         LogTools.warn("scs2.remote.robotModelFile is set but no such file: " + robotFile.getAbsolutePath()
               + " -- using the server-advertised model instead.");
         return null;
      }
      if (!robotFile.getName().toLowerCase().endsWith(".urdf"))
      {
         LogTools.warn("scs2.remote.robotModelFile must be a .urdf file, got: " + robotFile.getName()
               + " -- using the server-advertised model instead.");
         return null;
      }

      try
      {
         URDFTools.URDFParserProperties parserProperties = new URDFTools.URDFParserProperties();
         parserProperties.setSimplifyKinematics(false);
         parserProperties.setTransformToZUp(false);
         URDFModel urdfModel = URDFTools.loadURDFModel(robotFile, java.util.Collections.singletonList(robotFile.getParent()));
         RobotDefinition robotDefinition = URDFTools.toRobotDefinition(urdfModel, parserProperties);
         robotDefinition.sanitizeNames();
         LogTools.info("Overriding remote robot model with local URDF: " + robotFile.getAbsolutePath());
         return robotDefinition;
      }
      catch (Exception e)
      {
         LogTools.error("Failed to load scs2.remote.robotModelFile override from " + robotFile.getAbsolutePath() + ": "
               + e.getMessage() + " -- using the server-advertised model instead.");
         return null;
      }
   }

   public long getDelay()
   {
      return serverTimestamp.get() - latestDataTimestamp.get();
   }

   @Override
   public void addGraphicsAddedCallback(Consumer<List<YoGraphicDefinition>> addedGraphicsConsumer)
   {
   }

   @Override
   protected long computeRunTaskPeriod()
   {
      return Conversions.secondsToNanoseconds(0.01);
   }

   @Override
   protected long computePlaybackTaskPeriod()
   {
      /*
       * We let the yoVariableClient handle the bufferRecordTickPeriod feature, so we're not setting
       * updating the corresponding field in Session. Thus, it cannot compute the playback DT properly
       * without temporarily setting the Session.bufferRecordTickPeriod.
       */
      int superBufferRecordTickPeriod = super.getBufferRecordTickPeriod();
      super.setBufferRecordTickPeriod(bufferRecordTickPeriod);
      long playbackTaskPeriod = super.computePlaybackTaskPeriod();
      super.setBufferRecordTickPeriod(superBufferRecordTickPeriod);
      return playbackTaskPeriod;
   }

   @Override
   public SessionProperties getSessionProperties()
   {
      return new SessionProperties(getActiveMode(),
                                   getRunAtRealTimeRate(),
                                   getPlaybackRealTimeRate(),
                                   getSessionDTNanoseconds(),
                                   bufferRecordTickPeriod,
                                   getRunMaxDuration());
   }

   public void receivedTimestampOnly(long timestamp)
   {
      serverTimestamp.set(timestamp);
   }

   public void receivedTimestampAndData(long timestamp)
   {
      serverTimestamp.set(timestamp);

      if (!hasSessionStarted() || getActiveMode() != SessionMode.RUNNING)
         return;

      if (initializeServerUpdateRate)
      {
         updateServerUpdateRate();
         initializeServerUpdateRate = false;
         return;
      }

      latestDataTimestamp.set(timestamp);
      runTick();
   }

   private void reconnect()
   {
      if (yoVariableClientInterface.isConnected())
         return;

      sharedBuffer.setInPoint(sharedBuffer.getProperties().getCurrentIndex());

      try
      {
         if (yoVariableClientInterface.reconnect())
            updateServerUpdateRate();
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
   }

   private void disconnect()
   {
      if (!yoVariableClientInterface.isConnected())
         return;

      initializeServerUpdateRate = true;
      yoVariableClientInterface.disconnect();
   }

   @Override
   protected double doSpecificRunTick()
   {
      if (robotStateUpdater != null)
         robotStateUpdater.run();
      return Conversions.nanosecondsToSeconds(latestDataTimestamp.get());
   }

   @Override
   protected void initializeRunTick()
   {
      if (firstRunTick)
      {
         sharedBuffer.incrementBufferIndex(true);
         sharedBuffer.setInPoint(sharedBuffer.getProperties().getCurrentIndex());
         sharedBuffer.processLinkedPushRequests(false);
         nextRunBufferRecordTickCounter = 0;
         firstRunTick = false;
      }
      else if (nextRunBufferRecordTickCounter <= 0)
      {
         sharedBuffer.incrementBufferIndex(true);
         sharedBuffer.processLinkedPushRequests(false);
      }
   }

   @Override
   protected void finalizeRunTick(boolean forceWriteBuffer)
   {
      if (forceWriteBuffer || Conversions.nanosecondsToMilliseconds(getDelay()) < MAX_DELAY_MILLI * bufferRecordTickPeriod)
      {
         super.finalizeRunTick(forceWriteBuffer);
      }
      else
      {
         sharedBuffer.writeBuffer();
         processBufferRequests(false);
         publishBufferProperties(sharedBuffer.getProperties());
      }
   }

   public void receivedCommand(DataServerCommand command, int argument)
   {
      loggerStatusUpdater.updateStatus(command, argument);
   }

   @Override
   public void setBufferRecordTickPeriod(int bufferRecordTickPeriod)
   {
      if (bufferRecordTickPeriod == this.bufferRecordTickPeriod)
         return;
      this.bufferRecordTickPeriod = Math.max(1, bufferRecordTickPeriod);
      updateServerUpdateRate();
   }

   private void updateServerUpdateRate()
   {
      int updateRateInMilliseconds = (int) TimeUnit.NANOSECONDS.toMillis(bufferRecordTickPeriod * getSessionDTNanoseconds());
      yoVariableClientInterface.setVariableUpdateRate(updateRateInMilliseconds);
   }

   public void close()
   {
      if (yoVariableClientInterface != null)
      {
         if (yoVariableClientInterface.isConnected())
            yoVariableClientInterface.disconnect();
         yoVariableClientInterface.stop();
      }
   }

   public void sendCommandToYoVariableServer(DataServerCommand command, int argument)
   {
      if (yoVariableClientInterface != null && yoVariableClientInterface.isConnected())
         yoVariableClientInterface.sendCommand(command, argument);
   }

   @Override
   public String getSessionName()
   {
      return sessionName;
   }

   @Override
   public List<RobotDefinition> getRobotDefinitions()
   {
      return robotDefinitions;
   }

   @Override
   public List<TerrainObjectDefinition> getTerrainObjectDefinitions()
   {
      return Collections.emptyList();
   }

   @Override
   public List<YoGraphicDefinition> getYoGraphicDefinitions()
   {
      return yoGraphicDefinitions;
   }

   @Override
   public List<RobotStateDefinition> getCurrentRobotStateDefinitions(boolean initialState)
   {
      return robots.stream().map(Robot::getCurrentRobotStateDefinition).collect(Collectors.toList());
   }

   public LoggerStatusUpdater getLoggerStatusUpdater()
   {
      return loggerStatusUpdater;
   }
}
