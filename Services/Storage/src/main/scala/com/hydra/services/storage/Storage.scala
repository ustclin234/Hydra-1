package com.hydra.storage

import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.WeakHashMap
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.hydra.storage.HydraBinaryTableStorageElementExtension.HeadEntry

import scala.collection.JavaConverters._
import scala.math._

object Storage {
  private val Splitter = "[/\\\\]".r
  private val TimeFormatter = DateTimeFormatter.ofPattern("HH-mm-ss-SSS")
}

class Storage(val basePath: Path) {
  private val elementCache = new WeakHashMap[String, StorageElement]()
  private val rootElement = doGetStorageElement("/", false)
  private var permission: Permission = _
  elementCache.put("/", rootElement)
  if (!rootElement.exists) throw new IOException("BasePath [" + basePath.toString() + "] not exists.");

  def getStorageElement(path: String) = doGetStorageElement(path, false)

  def updatePermission(permission: Permission) {
    //TODO: System.out.println("Need check permission.")
    this.permission = permission
  }

  def clearPermission() {
    permission = null
  }

  def getPermission = permission

  def createTrashSpace(element: StorageElement) = {
    val dateTime = LocalDateTime.now()
    val date = dateTime.format(DateTimeFormatter.ISO_DATE)
    val time = dateTime.format(Storage.TimeFormatter)
    val trashSpace = basePath.resolve(Paths.get("..trash", date))
    if (!Files.exists(trashSpace, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(trashSpace)
    Files.createTempDirectory(trashSpace, "[" + time + "]")
  }

  //def getStorageElement(path: String, cacheable: Boolean) = doGetStorageElement(path, cacheable)
  def getHipInformation(path: String) = {
    val element = getStorageElement(path)
    if (!element.name.toLowerCase.endsWith(".hip") || element.getType != ElementType.Collection) throw new IOException(s"Path is not a HIP: $path")
    val elements = element.listElements
    val (note, items) = elements.indexWhere(e => e.name == "note") match {
      case -1 => ("", elements)
      case i => (new String(elements(i).readAll), elements.filter(_.name != "note"))
    }
    Map("note" -> note, "items" -> items.map(e => e.metaDataMap(true)))
  }

  def HBTFileInitialize(path: String, heads: List[List[String]]) = {
    val element = getStorageElement(path)
    if (!element.exists) element.createFile
    val hbtExt = HydraBinaryTableStorageElementExtension.initialize(element, heads)
  }

  def HBTFileAppendRows(path: String, rowsData: List[List[Any]]) = {
    val element = getStorageElement(path)
    val hbtExt = HydraBinaryTableStorageElementExtension.load(element)
    hbtExt.appendRows(rowsData)
  }

  def HBTFileReadRows(path: String, from: Int, count: Int) = {
    val element = getStorageElement(path)
    val hbtExt = HydraBinaryTableStorageElementExtension.load(element)
    hbtExt.readRows(from, count)
  }

  def HBTFileReadAllRows(path: String) = {
    val element = getStorageElement(path)
    val hbtExt = HydraBinaryTableStorageElementExtension.load(element)
    hbtExt.readAllRows
  }

  private def doGetStorageElement(path: String, cacheable: Boolean): StorageElement = {
    val formattedPath = formatPath(path)
    if (cacheable) elementCache.get(formattedPath).foreach(i => return i)
    val storageElement = new StorageElement(this, formattedPath)
    elementCache.put(formattedPath, storageElement)
    storageElement
  }

  private def formatPath(path: String) = {
    val pathSplitArray = Storage.Splitter.split(path)
    val sb = new StringBuilder()
    pathSplitArray.filterNot(i => i.isEmpty).foreach(i => sb.append('/').append(i))
    if (sb.length == 0) sb.append("/")
    sb.toString()
  }
}

class StorageServer(private val basePath: String) {
  private val storage = new Storage(Paths.get(basePath))

  def listElements(user: String, container: String) = {
    storage.updatePermission(new Permission(user))
    val containerElement = storage.getStorageElement(container)
    val elements = containerElement.listElements
    val results = new Array[String](elements.size)
    val iterator = elements.iterator
    for (i <- 0 to results.length) {
      val se = iterator.next
      results(i) = se.path
    }
    storage.clearPermission
    results
  }
}

class StorageElement(private val storage: Storage, val path: String) {

  import ElementType._
  import PermissionLevel._
  import PermissionDecision._

  val isRoot = "/".equals(path)
  val absolutePath = storage.basePath.resolve(path.substring(1))
  val parent = if (isRoot) null else storage.getStorageElement(path.substring(0, path.lastIndexOf("/")))
  val name = if (isRoot) "" else path.substring(path.lastIndexOf("/") + 1)
  val attribute = new StorageElementAttribute(if (isRoot) absolutePath.resolve("..root") else absolutePath.getParent().resolve("." + name))
  private var elementType: ElementType = _
  private var validation: Boolean = _
  private var fileLength: Long = -1
  private var creationTime: Long = 0
  private var lastAccessTime: Long = 0
  private var lastModifiedTime: Long = 0
  reload()

  def valid = validation

  def exists = getType != NotExist

  def getType = {
    validationVerify(valid, "Path [" + path + "] not valid.")
    permissionVerify(parent, Read)
    elementType
  }

  def getElement(path: String) = storage.getStorageElement(this.path + "/" + path)

  def listElements(): List[StorageElement] = listElements(true)

  def listElements(validOnly: Boolean) = {
    validationVerify(valid, "Path [" + path + "] not valid.")
    validationVerify(elementType == Collection, "Path [" + path + "] is not a Collection StorageElement.")
    permissionVerify(this, Read)
    val fileList = Files.list(absolutePath)
    val iterator = fileList.iterator()
    val elements = new ArrayBuffer[StorageElement]()
    while (iterator.hasNext()) {
      val relativize = absolutePath.relativize(iterator.next())
      val storageElement = getElement(relativize.toString())
      if ((!validOnly) || (storageElement.valid && (storageElement.getType == Collection || storageElement.getType == Content)))
        elements += (storageElement)
    }
    fileList.close
    elements.toList
  }

  def createDirectories() {
    validationVerify(valid, "Path [" + path + "] not valid.");
    if (exists) throw new IOException("Directory exists.")
    if (parent == null) throw new IOException("RootElement can not be created.")
    permissionVerify(parent, Append)
    Files.createDirectories(absolutePath)
    var e = this
    while (e != null && !e.exists) {
      e.reload()
      e = e.parent
    }
  }

  def createFile() {
    validationVerify(valid, "Path [" + path + "] not valid.")
    if (exists) throw new IOException("File exists.")
    if (parent == null) throw new IOException("RootElement can not be created.")
    permissionVerify(parent, Append)
    if (!parent.exists) parent.createDirectories()
    Files.createFile(absolutePath)
    reload()
  }

  def size = {
    validationVerify(getType == Content, "Path [" + path + "] is not content.")
    permissionVerify(this, Read)
    fileLength
  }

  def read(start: Long, length: Int) = {
    validationVerify(getType == Content, "Path [" + path + "] is not content.")
    permissionVerify(this, Read)
    validationVerify(start + length <= fileLength, s"Out of file size: ${start + length} > ${fileLength}.")
    val buffer = new Array[Byte](length)
    val raf = new RandomAccessFile(absolutePath.toFile, "r")
    raf.seek(start)
    raf.read(buffer)
    raf.close
    buffer
  }

  def readAll = {
    validationVerify(getType == Content, "Path [" + path + "] is not content.")
    permissionVerify(this, Read)
    read(0, size.toInt)
  }

  def append(data: Array[Byte]) {
    validationVerify(getType == Content, "Path [" + path + "] is not content.")
    permissionVerify(this, Append)
    val raf = new RandomAccessFile(absolutePath.toFile, "rw")
    raf.seek(fileLength)
    raf.write(data)
    raf.close
    reload()
  }

  def write(data: Array[Byte], start: Long) {
    validationVerify(getType == Content, "Path [" + path + "] is not content.")
    permissionVerify(this, Modify)
    val raf = new RandomAccessFile(absolutePath.toFile, "rw")
    raf.seek(start)
    raf.write(data)
    raf.close
    reload()
  }

  def clear {
    validationVerify(getType == Content, "Path [" + path + "] is not content.")
    permissionVerify(this, Modify)
    val raf = new RandomAccessFile(absolutePath.toFile, "rw")
    raf.setLength(0)
    raf.close
    reload()
  }

  def delete() {
    validationVerify(valid, "Path [" + path + "] not valid.")
    if (!exists) throw new IOException("Path not exists.")
    if (parent == null) throw new IOException("RootElement can not be deleted.")
    permissionVerify(parent, Modify)
    getType match {
      case Collection =>
      case Content =>
      case _ => throw new IOException("Element " + this + " can not be deleted.")
    }
    val trashSpace = storage.createTrashSpace(this)
    val trashSpot = trashSpace.resolve(parent.path.substring(1))
    if (!Files.exists(trashSpot, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(trashSpot)
    Files.move(absolutePath, trashSpot.resolve(absolutePath.getFileName()))
    reload()
  }

  def getCreationTime = creationTime

  def getLastAccessTime = lastAccessTime

  def getLastModifiedTime = lastModifiedTime

  def metaDataMap(withTime: Boolean = false) = {
    val metaData = new HashMap[String, Any]
    val elementType = getType
    metaData += "Name" -> name
    metaData += "Path" -> path
    metaData += "Type" -> elementType.toString
    if (withTime) {
      metaData += "CreationTime" -> getCreationTime
      metaData += "LastAccessTime" -> getLastAccessTime
      metaData += "LastModifiedTime" -> getLastModifiedTime
    }
    if (elementType == Content) {
      metaData += "Size" -> size
    }
    metaData
  }

  override def toString = s"${this.getClass().getSimpleName()}[${path}]"

  private def reload() {
    validation = (parent == null || parent.validation) && nameValid(name)
    Files.exists(absolutePath, LinkOption.NOFOLLOW_LINKS) match {
      case true => {
        val attributes = Files.readAttributes[BasicFileAttributes](absolutePath, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
        if (attributes.isDirectory()) {
          elementType = Collection
        } else if (attributes.isRegularFile()) {
          elementType = Content
          fileLength = attributes.size
        } else {
          elementType = Unknown
        }
        creationTime = attributes.creationTime.toMillis
        lastAccessTime = attributes.lastAccessTime.toMillis
        lastModifiedTime = attributes.lastModifiedTime.toMillis
      }
      case false => elementType = NotExist
    }
    if (valid && (elementType == Collection || elementType == Content)) attribute.reload()
  }

  private def nameValid(name: String) = !name.startsWith(".")

  private def validationVerify(validation: Boolean, errorMessage: String) = if (!validation) throw new IOException(errorMessage)

  private def permissionVerify(storageElement: StorageElement, requiredLevel: PermissionLevel) {
    if (storageElement == null) {
      if (permission(Read, requiredLevel) == Deny) {
        throw new PermissionDeniedException("Do not have the access to Parent of RootElement")
      }
    } else {
      val permission = storage.getPermission
      var element = storageElement
      while (element != null) {
        element.attribute.permissionVerify(permission, requiredLevel) match {
          case Accept => return
          case Deny => throw new PermissionDeniedException("Do not have the access of " + requiredLevel + " to element " + storageElement);
          case Undefined => element = element.parent
        }
      }
    }
  }
}

object ElementType extends Enumeration {
  type ElementType = Value
  val Collection, Content, NotExist, Unknown = Value
}

class StorageElementAttribute(path: Path) {

  import PermissionLevel._

  private val permissionLevelMap = new HashMap[String, PermissionLevel.Value]()
  private var exists: Boolean = _
  private var defaultPermissionLevel: PermissionLevel = null

  def reload() {
    exists = Files.exists(path, LinkOption.NOFOLLOW_LINKS)
    if (exists) {
      try {
        Files.readAllLines(path).asScala.foreach(line => {
          val split = line.split(" *: *", 2)
          if (split.length == 2) {
            val key = split(0)
            val value = split(1)
            if (key != null && key.startsWith("PERMISSION.")) {
              val name = key.substring(11)
              try {
                val level = Integer.parseInt(value)
                name match {
                  case "DEFAULT" => defaultPermissionLevel = PermissionLevel.getLevel(level);
                  case _ => permissionLevelMap.put(name, PermissionLevel.getLevel(level));
                }
              } catch {
                case e: NumberFormatException =>
              }
            }
          }
        })
      } catch {
        case e: Throwable =>
      }
    }
  }

  def permissionVerify(permission: Permission, requiredLevel: PermissionLevel) = {
    permission match {
      case null => PermissionLevel.permission(defaultPermissionLevel, requiredLevel)
      case _ => permissionLevelMap.get(permission.name) match {
        case None => PermissionLevel.permission(defaultPermissionLevel, requiredLevel)
        case Some(level) => PermissionLevel.permission(level, requiredLevel)
      }
    }
  }
}

object HydraBinaryTableStorageElementExtension {

  def initialize(element: StorageElement, heads: List[List[String]]) = {
    if (element.size > 0) throw new IOException(s"Can not initialize an non-empty StorageElement.")
    if (!element.name.toLowerCase.endsWith(".hbt")) throw new IOException(s"Invalid StorageElement. Should be .hbt file.")
    val headEntries = heads.map(e => new HeadEntry(e(0), e(1)))
    val headEntriesString = headEntries.map(e => s"${e.dataType}:${e.title}").mkString("\n")

    val headString = headEntriesString
    val headBytes = headString.getBytes("UTF-8")
    val buffer = ByteBuffer.allocate(8 + headBytes.size)
    buffer.put(Array[Byte]('H', 'B', 'T', 0))
    buffer.putInt(headBytes.size)
    buffer.put(headBytes)
    element.append(buffer.array)
  }

  def load(element: StorageElement) = {
    if (!element.name.toLowerCase.endsWith(".hbt")) throw new IOException(s"Invalid StorageElement. Should be .hbt file.")
    val headMeta = ByteBuffer.wrap(element.read(0, 8))
    val headSize = headMeta.getInt(4)
    val headBytes = element.read(8, headSize)
    val headString = new String(headBytes, "UTF-8")

    val headEntriesString = headString
    val headEntries = headEntriesString.split("\n").toList.map(line => {
      val s = line.split(":", 2)
      new HeadEntry(s(1), s(0))
    })
    new HydraBinaryTableStorageElementExtension(element, headEntries, 8 + headSize)
  }

  val acceptableTypes = Map("Byte" -> 1, "Short" -> 2, "Int" -> 4, "Long" -> 8, "Float" -> 4, "Double" -> 8)

  class HeadEntry(val title: String, val dataType: String) {
    if (!acceptableTypes.contains(dataType)) throw new IOException(s"Data type ${dataType} is not acceptable.")
    val dataLength = acceptableTypes(dataType)
  }

}

class HydraBinaryTableStorageElementExtension(val element: StorageElement, val headEntries: List[HeadEntry], private val headLength: Int) {
  private val rowDataLength = headEntries.map(e => e.dataLength).sum

  def appendRows(rowsData: List[List[Any]]) = {
    val rowCount = rowsData.size
    val buffer = ByteBuffer.allocate(rowDataLength * rowCount)
    rowsData.foreach(r => pushRowData(buffer, r))
    element.append(buffer.array)
  }

  def readRows(from: Int, count: Int) = {
    val bytes = element.read(headLength + from * rowDataLength, count * rowDataLength)
    val buffer = ByteBuffer.wrap(bytes)
    Range(0, count).toList.map(r => {
      headEntries.map(headEntry => {
        headEntry.dataType match {
          case "Byte" => buffer.get
          case "Short" => buffer.getShort
          case "Int" => buffer.getInt
          case "Long" => buffer.getLong
          case "Float" => buffer.getFloat
          case "Double" => buffer.getDouble
          case _ => None
        }
      })
    })
  }

  def readAllRows = readRows(0, ((element.size - headLength) / rowDataLength).toInt)

  private def pushRowData(buffer: ByteBuffer, rowData: List[Any]) = {
    if (rowData.size != headEntries.size) throw new IOException(s"Row Data size not match. Should be ${headEntries.size}.")
    rowData.zip(headEntries).foreach(zip => {
      val data = zip._1
      val dataType = zip._2.dataType
      if (data.isInstanceOf[Byte]) {
        val d = data.asInstanceOf[Byte]
        dataType match {
          case "Byte" => buffer.put(d)
          case "Short" => buffer.putShort(d)
          case "Int" => buffer.putInt(d)
          case "Long" => buffer.putLong(d)
          case "Float" => buffer.putFloat(d)
          case "Double" => buffer.putDouble(d)
        }
      } else if (data.isInstanceOf[Short]) {
        val d = data.asInstanceOf[Short]
        dataType match {
          case "Byte" => buffer.put(d.toByte)
          case "Short" => buffer.putShort(d)
          case "Int" => buffer.putInt(d)
          case "Long" => buffer.putLong(d)
          case "Float" => buffer.putFloat(d)
          case "Double" => buffer.putDouble(d)
        }
      } else if (data.isInstanceOf[Int]) {
        val d = data.asInstanceOf[Int]
        dataType match {
          case "Byte" => buffer.put(d.toByte)
          case "Short" => buffer.putShort(d.toShort)
          case "Int" => buffer.putInt(d)
          case "Long" => buffer.putLong(d)
          case "Float" => buffer.putFloat(d)
          case "Double" => buffer.putDouble(d)
        }
      } else if (data.isInstanceOf[Long]) {
        val d = data.asInstanceOf[Long]
        dataType match {
          case "Byte" => buffer.put(d.toByte)
          case "Short" => buffer.putShort(d.toShort)
          case "Int" => buffer.putInt(d.toInt)
          case "Long" => buffer.putLong(d)
          case "Float" => buffer.putFloat(d)
          case "Double" => buffer.putDouble(d)
        }
      } else if (data.isInstanceOf[Float]) {
        val d = data.asInstanceOf[Float]
        dataType match {
          case "Byte" => buffer.put(d.toByte)
          case "Short" => buffer.putShort(d.toShort)
          case "Int" => buffer.putInt(d.toInt)
          case "Long" => buffer.putLong(d.toLong)
          case "Float" => buffer.putFloat(d)
          case "Double" => buffer.putDouble(d)
        }
      } else if (data.isInstanceOf[Double]) {
        val d = data.asInstanceOf[Double]
        dataType match {
          case "Byte" => buffer.put(d.toByte)
          case "Short" => buffer.putShort(d.toShort)
          case "Int" => buffer.putInt(d.toInt)
          case "Long" => buffer.putLong(d.toLong)
          case "Float" => buffer.putFloat(d.toFloat)
          case "Double" => buffer.putDouble(d)
        }
      }
    })
  }
}

class Permission(val name: String) {
}

object PermissionDecision extends Enumeration {
  type PermissionDecision = Value
  val Accept, Deny, Undefined = Value
}

object PermissionLevel extends Enumeration {
  type PermissionLevel = Value
  val NoAccess, Read, Append, Modify, PermissionRead, PermisionAppend, PermissionModify = Value

  def getLevel(level: Int) = PermissionLevel(min(max(0, level), PermissionLevel.maxId - 1))

  def permission(permissionLevel: PermissionLevel, requiredLevel: PermissionLevel) =
    if (permissionLevel == null || requiredLevel == null) PermissionDecision.Undefined
    else if (permissionLevel.id >= requiredLevel.id) PermissionDecision.Accept else PermissionDecision.Deny
}

class PermissionDeniedException(message: String, cause: Throwable) extends Exception(message, cause) {
  def this(message: String) {
    this(message, null)
  }
}
