__author__ = 'Hwaipy'

from Instruments import DeviceException, Instrument
from socket import *

class FTDC(Instrument):
    def __init__(self, ip):
        super().__init__()
        self.ip = ip

    def readCode(self, channel):
        self._varifyChannel(channel)
        tcpCliSock = socket(AF_INET, SOCK_STREAM)
        tcpCliSock.connect((self.ip, 5000))
        command = 'DA={};RW=0;ADDR=0x01;VAL=0xFFFFF;'.format(channel)
        tcpCliSock.send(bytearray(command, 'UTF-8'))
        buffer = ''
        while True:
            c = tcpCliSock.recv(1)
            if c == b'\n':
                break
            buffer += chr(c[0])
        tcpCliSock.close()
        code = int(buffer, 16)
        return code

    def readVoltage(self, channel):
        code = self.readCode(channel)12
        return (code - 0x80000) / 0x80000 * 7.0

    def setCode(self, channel, code):
        self._varifyChannel(channel)
        if code < 0 or code > 0xFFFFF:
            raise DeviceException('code should be in [0x0, 0xFFFFF].')
        tcpCliSock = socket(AF_INET, SOCK_STREAM)
        tcpCliSock.connect((self.ip, 5000))
        command = 'DA={};RW=1;ADDR=0x01;VAL={};'.format(channel, hex(code))
        tcpCliSock.send(bytearray(command, 'UTF-8'))
        tcpCliSock.close()

    def setVoltage(self, channel, voltage):
        if voltage < -7 or voltage > 7:
            raise DeviceException('voltage should be in [-7, 7].')
        self.setCode(channel, int(voltage / 7 * 0x80000) + 0x80000)

    def increaseVoltage(self, channel, dV):
        self.setVoltage(channel, self.readVoltage(channel) + dV)

    def increaseCode(self, channel, dC):
        self.setCode(channel, self.readCode(channel) + dC)

    def _varifyChannel(self, channel):
        if channel < 1 or channel > 4:
            raise DeviceException('channel should be in [1, 4].')
        if int(channel) != channel:
            raise DeviceException('channel should be an Int.')

if __name__ == '__main__':
    import sys

    FTDC('10.0.200.22').setVoltage(1, 0.0)
    sys.exit(0)

    session1 = Pydra.Session.newSession(('192.168.25.27', 20102), FTDC('192.168.25.4'), 'FTDC-Alice')
    for line in sys.stdin:
        if line == 'q\n':
            break
    session1.stop()