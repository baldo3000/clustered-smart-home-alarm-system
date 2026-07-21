package com.baldo3000.cshas

import actors.SensorActor.SensorType

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

object HomeAlarmProtocol:
  type Zone = String

  @JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type"
  )
  @JsonSubTypes(
    Array(
      new JsonSubTypes.Type(value = classOf[ArmConfig.None.type], name = "None"),
      new JsonSubTypes.Type(value = classOf[ArmConfig.Full.type], name = "Full"),
      new JsonSubTypes.Type(value = classOf[ArmConfig.Partial], name = "Partial")
    )
  )
  sealed trait ArmConfig extends CborSerializable
  object ArmConfig:
    case object None extends ArmConfig
    case object Full extends ArmConfig
    final case class Partial(zone: Zone) extends ArmConfig
  
  case class SensorConfig(sensorID: String, zones: Set[Zone], sensorType: SensorType)
      extends CborSerializable
