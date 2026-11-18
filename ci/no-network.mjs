// SPDX-License-Identifier: Apache-2.0
import http from 'node:http';
import https from 'node:https';
import net from 'node:net';
import dgram from 'node:dgram';
import tls from 'node:tls';
import { syncBuiltinESMExports } from 'node:module';
const denied = () => { throw new Error('Unexpected network operation; inject a mock transport'); };
globalThis.fetch = denied;
for (const module of [http, https]) { module.request = denied; module.get = denied; }
net.connect = denied;
net.createConnection = denied;
net.Socket.prototype.connect = denied;
net.Server.prototype.listen = denied;
tls.connect = denied;
dgram.createSocket = denied;
dgram.Socket.prototype.connect = denied;
dgram.Socket.prototype.send = denied;
dgram.Socket.prototype.bind = denied;
syncBuiltinESMExports();
