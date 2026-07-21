package com.baldo3000.cshas
package actors

import HomeAlarmProtocol.ArmConfig
import actors.ControlUnitActor.Command.PINEntered

import org.apache.pekko.actor.typed.receptionist.Receptionist
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

object KeypadActor:
  sealed trait Command extends CborSerializable
  object Command:
    final case class EnterPIN(pin: Int, armConfig: ArmConfig) extends Command
    final case class ControlUnitsUpdated(
        controlUnits: Set[ActorRef[ControlUnitActor.Command]]
    ) extends Command

  export Command.*

  def apply(): Behavior[Command] = Behaviors.setup: ctx =>
    val listingAdapter = ctx.messageAdapter[Receptionist.Listing]:
      case ControlUnitActor.serviceKey.Listing(controlUnits) =>
        ControlUnitsUpdated(controlUnits)

    ctx.system.receptionist ! Receptionist.Subscribe(
      ControlUnitActor.serviceKey,
      listingAdapter
    )
    active(Set())

  private def active(
      controlUnits: Set[ActorRef[ControlUnitActor.Command]]
  ): Behavior[Command] =
    Behaviors.receive: (ctx, message) =>
      message match
        case EnterPIN(pin, armConfig) =>
          ctx.log.info(s"Entered PIN: $pin")
          controlUnits.foreach(_ ! PINEntered(pin, armConfig)) // TODO: check
          Behaviors.same
        case ControlUnitsUpdated(controlUnits) =>
          ctx.log.info(s"Control units updated in keypad: $controlUnits")
          active(controlUnits)
