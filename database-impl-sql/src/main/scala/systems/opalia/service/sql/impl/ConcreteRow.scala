package systems.opalia.service.sql.impl

import java.time.{LocalDate, LocalDateTime, LocalTime}
import scala.collection.immutable.ListMap
import systems.opalia.commons.codec.Hex
import systems.opalia.interfaces.database._
import systems.opalia.interfaces.json.JsonAst


class ConcreteRow(row: ListMap[String, Any])
  extends Row {

  protected def find(column: String): Option[Any] =
    row.find(_._1.equalsIgnoreCase(column)).map(_._2)

  def toJson: JsonAst.JsonObject =
    JsonAst.JsonObject(row.map(x => (x._1, transform(x._2))))

  private def transform(value: Any): JsonAst.JsonValue =
    value match {

      case null => JsonAst.JsonNull
      case x: Boolean => JsonAst.JsonBoolean(x)
      case x: Byte => JsonAst.JsonNumberByte(x)
      case x: Short => JsonAst.JsonNumberShort(x)
      case x: Integer => JsonAst.JsonNumberInt(x)
      case x: Long => JsonAst.JsonNumberLong(x)
      case x: Float => JsonAst.JsonNumberFloat(x)
      case x: Double => JsonAst.JsonNumberDouble(x)
      case x: Char => JsonAst.JsonString(x.toString)
      case x: String => JsonAst.JsonString(x)
      case x: BigDecimal => JsonAst.JsonNumberBigDecimal(x)
      case x: LocalDate => JsonAst.JsonString(x.toString)
      case x: LocalTime => JsonAst.JsonString(x.toString)
      case x: LocalDateTime => JsonAst.JsonString(x.toString)

      case x: Seq[_] if (x.forall(_.isInstanceOf[Byte])) =>
        JsonAst.JsonString(Hex.encode(x.map(_.asInstanceOf[Byte])))

      case _ =>
        throw new IllegalArgumentException(
          s"Cannot build JSON AST with value $value (${value.getClass.getName}).")
    }
}
