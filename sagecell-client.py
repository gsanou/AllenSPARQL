#!/usr/bin/env python
"""
A small client illustrating how to interact with the Sage Cell Server, version 2

Requires the websocket-client package: http://pypi.python.org/pypi/websocket-client
"""

import websocket
import json
import requests


class SageCell(object):

    def __init__(self, url, timeout=120):
        if not url.endswith('/'):
            url += '/'
        # POST or GET <url>/kernel
        # if there is a terms of service agreement, you need to
        # indicate acceptance in the data parameter below (see the API docs)
        response = requests.post(
            url + 'kernel',
            data={'accepted_tos': 'true'},
            headers={'Accept': 'application/json'}).json()
        # RESPONSE: {"id": "ce20fada-f757-45e5-92fa-05e952dd9c87", "ws_url": "ws://localhost:8888/"}
        # construct the websocket channel url from that
        self.kernel_url = '{ws_url}kernel/{id}/'.format(**response)
        #print (self.kernel_url)
        websocket.setdefaulttimeout(timeout)
        self._ws = websocket.create_connection(
            self.kernel_url + 'channels',
            header={'Jupyter-Kernel-ID': response['id']})
        # initialize our list of messages
        self.shell_messages = []
        self.iopub_messages = []

    def execute_request(self, code):
        # zero out our list of messages, in case this is not the first request
        self.shell_messages = []
        self.iopub_messages = []

        # Send the JSON execute_request message string down the shell channel
        msg = self._make_execute_request(code)
        self._ws.send(msg)

        # Wait until we get both a kernel status idle message and an execute_reply message
        got_execute_reply = False
        got_idle_status = False
        while not (got_execute_reply and got_idle_status):
            msg = json.loads(self._ws.recv())
            if msg['channel'] == 'shell':
                self.shell_messages.append(msg)
                # an execute_reply message signifies the computation is done
                if msg['header']['msg_type'] == 'execute_reply':
                    got_execute_reply = True
            elif msg['channel'] == 'iopub':
                self.iopub_messages.append(msg)
                # the kernel status idle message signifies the kernel is done
                if (msg['header']['msg_type'] == 'status' and
                    msg['content']['execution_state'] == 'idle'):
                        got_idle_status = True

        return {'shell': self.shell_messages, 'iopub': self.iopub_messages}
        #return { 'iopub': self.iopub_messages}

    def _make_execute_request(self, code):
        from uuid import uuid4
        session = str(uuid4())

        # Here is the general form for an execute_request message
        execute_request = {
            'channel': 'shell',
            'header': {
                'msg_type': 'execute_request',
                'msg_id': str(uuid4()), 
                'username': '', 'session': session,
            },
            'parent_header':{},
            'metadata': {},
            'content': {
                'code': code, 
                'silent': False, 
                'user_expressions': {
                    '_sagecell_files': 'sys._sage_.new_files()',
                }, 
                'allow_stdin': False,
            }
        }
        return json.dumps(execute_request)

    def close(self):
        # If we define this, we can use the closing() context manager to automatically close the channels
        self._ws.close()

if __name__ == "__main__":
    import sys
    if len(sys.argv) >= 2:
        # argv[1] is the web address
        url = sys.argv[1]
    else:
        url = 'https://sagecell.sagemath.org'
    a = SageCell(url)
    print(a.execute_request(sys.argv[2]))
	#import pprint
    #pprint.pprint(a.execute_request(sys.argv[2]))