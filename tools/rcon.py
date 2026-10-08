"""Minimal RCON client for the dev server. Usage: RCON_PASSWORD=... python3 tools/rcon.py "<command>" ["sleep 2"] ..."""
import os
import socket
import struct
import sys
import time


def packet(request_id, kind, body):
    data = struct.pack('<ii', request_id, kind) + body.encode() + b'\x00\x00'
    return struct.pack('<i', len(data)) + data


def read(sock):
    length = struct.unpack('<i', sock.recv(4))[0]
    data = b''
    while len(data) < length:
        data += sock.recv(length - len(data))
    return data[8:-2].decode('utf-8', 'replace')


def main():
    sock = socket.create_connection(('127.0.0.1', int(os.environ.get('RCON_PORT', '25575'))), timeout=60)
    sock.sendall(packet(1, 3, os.environ['RCON_PASSWORD']))
    read(sock)
    for command in sys.argv[1:]:
        if command.startswith('sleep '):
            time.sleep(float(command.split()[1]))
            continue
        sock.sendall(packet(2, 2, command))
        print(read(sock))


if __name__ == '__main__':
    main()
