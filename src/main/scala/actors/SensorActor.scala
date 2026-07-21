package com.baldo3000.cshas
package actors

import HomeAlarmProtocol.SensorConfig
import actors.ControlUnitActor.Command.SensorTriggered

import org.apache.pekko.actor.typed.receptionist.Receptionist
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

object SensorActor:
  enum SensorType extends CborSerializable:
    case Motion, Barrier

  sealed trait Command extends CborSerializable
  object Command:
    case object Trigger extends Command
    final case class ControlUnitsUpdated(
        controlUnits: Set[ActorRef[ControlUnitActor.Command]]
    ) extends Command

  export Command.*

  def apply(sensorConfig: SensorConfig): Behavior[Command] = Behaviors.setup:
    ctx =>
      val listingAdapter = ctx.messageAdapter[Receptionist.Listing]:
        case ControlUnitActor.serviceKey.Listing(controlUnits) =>
          ControlUnitsUpdated(controlUnits)

      ctx.system.receptionist ! Receptionist.Subscribe(
        ControlUnitActor.serviceKey,
        listingAdapter
      )
      active(sensorConfig, Set())

  private def active(
      sensorConfig: SensorConfig,
      controlUnits: Set[ActorRef[ControlUnitActor.Command]]
  ): Behavior[Command] =
    Behaviors.receive: (ctx, message) =>
      message match
        case Trigger =>
          ctx.log.info(s"Sensor ${sensorConfig.sensorID} triggered")
          controlUnits.foreach(_ ! SensorTriggered(sensorConfig))
          Behaviors.same
        case ControlUnitsUpdated(controlUnits) =>
          ctx.log.info(s"Control units updated in sensor: $controlUnits")
          active(sensorConfig, controlUnits)
