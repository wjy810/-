"""Runs inside the isolated ClamAV container network; never writes a malware fixture."""
import json
import socket
import struct


def command(value, data=None):
    with socket.create_connection(("127.0.0.1", 3310), timeout=30) as connection:
        connection.sendall(b"z" + value + b"\0")
        if data is not None:
            connection.sendall(struct.pack("!I", len(data)) + data + b"\0\0\0\0")
        return connection.recv(8192).decode().rstrip("\0\n")


version = command(b"VERSION")
clean = command(b"INSTREAM", b"Synthetic clean JobProof scanner verification")
eicar = b"X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
infected = command(b"INSTREAM", eicar)
assert clean.endswith(" OK"), clean
assert "FOUND" in infected and "Eicar" in infected, infected
print(json.dumps({"status": "PASS", "version": version, "clean": clean, "testSignature": infected}, indent=2))
