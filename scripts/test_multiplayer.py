#!/usr/bin/env python3
"""Five TCP clients exercise full Jamb match, scoring guards, reconnection, and rematch."""
import base64
import queue
import socket
import subprocess
import sys
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def b64(text):
    return base64.b64encode(text.encode('utf-8')).decode('ascii')


def props(blob):
    out = {}
    for line in blob.decode('latin-1').splitlines():
        if line and line[0] not in '#!' and '=' in line:
            key, value = line.split('=', 1)
            out[key] = value
    return out


class Client:
    def __init__(self, port, name, token=''):
        self.name = name
        self.sock = socket.create_connection(('127.0.0.1', port), 5)
        self.sock.settimeout(None)
        self.events = queue.Queue()
        self.reader = threading.Thread(target=self._read, daemon=True)
        self.reader.start()
        self.send('HELLO|' + b64(name) + '|' + b64(token))

    def _read(self):
        try:
            stream = self.sock.makefile('rb')
            while line := stream.readline():
                parts = line.decode('ascii').rstrip('\r\n').split('|', 1)
                if len(parts) != 2:
                    continue
                data = base64.b64decode(parts[1])
                kind = parts[0]
                self.events.put((kind, props(data) if kind == 'STATE' else data.decode('utf-8')))
        except OSError:
            pass

    def send(self, line):
        self.sock.sendall((line + '\n').encode('ascii'))

    def until(self, kind, predicate=lambda _: True, timeout=20):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                key, data = self.events.get(timeout=max(0.001, deadline-time.monotonic()))
            except queue.Empty:
                break
            if key == 'ERROR' and kind != 'ERROR':
                raise AssertionError(f'{self.name}: unexpected error {data}')
            if key == kind and predicate(data):
                return data
        raise AssertionError(f'{self.name}: timeout waiting for {kind}')

    def close(self):
        try:
            self.sock.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        self.sock.close()


def choose_score(st, player):
    for col in (0, 1, 2, 3):
        rows = range(13) if col != 1 else range(12, -1, -1)
        for row in rows:
            if st[f'p{player}.c{col}.r{row}'] == '-1':
                return col, row
    raise AssertionError('Player is already done')


def run():
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 0))
        port = probe.getsockname()[1]
    server = subprocess.Popen(['java', '-cp', str(ROOT / 'build/classes'), 'com.jamb.Main', '--server', str(port)],
                              stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    users = []
    try:
        for _ in range(80):
            try:
                with socket.create_connection(('127.0.0.1', port), .1):
                    break
            except OSError:
                time.sleep(.05)
        for i in range(5):
            c = Client(port, 'TestPlayer' + str(i))
            users.append(c)
            c.token = c.until('WELCOME')
            c.until('STATE', lambda s: s.get('players') == str(i+1))
        blocked = Client(port, 'PlayerSix')
        message = blocked.until('ERROR')
        assert 'full' in message.lower(), message
        blocked.close()
        users[0].send('START')
        for c in users:
            c.until('STATE', lambda s: s.get('phase') == 'PLAYING' and s.get('players') == '5')
        users[1].send('ROLL')
        assert 'not your turn' in users[1].until('ERROR').lower()
        users[0].send('ROLL')
        state = users[0].until('STATE', lambda s: s.get('rolls') == '1')
        first = state['die0']
        assert all(1 <= int(state[f'die{d}']) <= 6 for d in range(5))
        # Five-dice starter must not pretend that a sixth die exists yet.
        assert 'die5' not in state
        users[0].send('HOLD|5')
        assert 'invalid number' in users[0].until('ERROR').lower()
        users[0].send('HOLD|0')
        users[0].until('STATE', lambda s: s.get('held0') == 'true')
        users[0].send('ROLL')
        state = users[0].until('STATE', lambda s: s.get('rolls') == '2')
        assert first == state['die0'], 'Held die changed'
        users[0].send('ROLL')
        users[0].until('STATE', lambda s: s.get('rolls') == '3')
        users[0].send('ROLL')
        assert 'three rolls' in users[0].until('ERROR').lower()
        users[0].send('SCORE|0|1')
        assert 'order' in users[0].until('ERROR').lower()
        users[0].send('SCORE|0|0')
        users[0].until('STATE', lambda s: s.get('move') == '1' and s.get('turn') == '1')
        users[1].send('ROLL')
        users[1].until('STATE', lambda s: s.get('move') == '1' and s.get('rolls') == '1')
        users[1].send('ANNOUNCE|12')
        users[1].until('STATE', lambda s: s.get('announced') == '12')
        users[1].send('SCORE|2|12')
        assert 'announced' in users[1].until('ERROR').lower()
        users[1].send('SCORE|3|12')
        users[1].until('STATE', lambda s: s.get('move') == '2' and s.get('turn') == '2')

        move = 2
        while move < 260:
            active = move % 5
            if move == 3:
                users[3].close()
                offline = users[0].until('STATE', lambda s: s.get('move') == '3' and s.get('p3.online') == 'false')
                assert offline['turn'] == '3'
                fake = Client(port, 'TestPlayer3', 'wrong-token')
                assert 'reserved' in fake.until('ERROR').lower()
                fake.close()
                users[0].send('SKIP')
                users[0].until('STATE', lambda s: s.get('move') == '4')
                real = Client(port, 'TestPlayer3', users[3].token)
                real.token = real.until('WELCOME')
                real.until('STATE', lambda s: s.get('p3.online') == 'true')
                users[3] = real
                move += 1
                continue
            current = users[active].until('STATE', lambda s: s.get('phase') == 'PLAYING' and s.get('move') == str(move)
                                         and s.get('turn') == str(active) and s.get('rolls') == '0')
            col, row = choose_score(current, active)
            users[active].send('ROLL')
            if col == 3:
                users[active].send('ANNOUNCE|' + str(row))
            users[active].send(f'SCORE|{col}|{row}')
            move += 1
        final = users[0].until('STATE', lambda s: s.get('phase') == 'FINISHED' and s.get('move') == '260')
        assert all(final[f'p{p}.c{c}.r{r}'] != '-1' for p in range(5) for c in range(4) for r in range(13))
        totals = [int(final[f'p{i}.total']) for i in range(5)]
        users[0].send('RESTART')
        again = users[0].until('STATE', lambda s: s.get('phase') == 'PLAYING' and s.get('move') == '0')
        assert all(again[f'p{p}.c{c}.r{r}'] == '-1' for p in range(5) for c in range(4) for r in range(13))
        print('PASS: 5-player full game (260 moves), dice holding, guardrails, 5-seat limit,')
        print('      announcing, disconnection, authenticated reconnection, skip, winner, rematch')
        print('Final scores:', totals)
    finally:
        for c in users:
            c.close()
        server.terminate()
        try:
            server.wait(timeout=4)
        except subprocess.TimeoutExpired:
            server.kill()
        if server.stderr:
            err = server.stderr.read().decode('utf-8', errors='replace')
            if err:
                print('Server log:', err, file=sys.stderr)


if __name__ == '__main__':
    run()
