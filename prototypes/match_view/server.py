"""Single-thread loopback-only HTTP, fixed routes, no filesystem API."""
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
from pathlib import Path
import secrets
from prototypes.phase4f.protocol import parse_json
from .app import CommandError, MatchController

STATIC=Path(__file__).resolve().parent/"static"
BODY_LIMIT=4096
TOKEN_HEADER="X-Match-Token"


class MatchServer(HTTPServer):
    def __init__(self,controller=None):
        self.controller=MatchController() if controller is None else controller
        self.token=secrets.token_urlsafe(32)
        super().__init__(("127.0.0.1",0),Handler)
        self.origin="http://127.0.0.1:"+str(self.server_port)
        self.allowed_host="127.0.0.1:"+str(self.server_port)


class Handler(BaseHTTPRequestHandler):
    server_version="MatchView";sys_version=""

    def log_message(self,format,*args):
        # Do not log request paths, headers, secrets, or raw client input.
        pass

    def _reply(self,status,body,content_type="application/json; charset=utf-8"):
        if type(body) is not bytes:body=(json.dumps(body,ensure_ascii=False,allow_nan=False)+"\n").encode("utf-8")
        self.send_response(status);self.send_header("Content-Type",content_type);self.send_header("Content-Length",str(len(body)))
        self.send_header("Cache-Control","no-store");self.send_header("X-Content-Type-Options","nosniff")
        self.send_header("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'none'; frame-ancestors 'none'; form-action 'none'; base-uri 'none'")
        self.send_header("Connection","close");self.end_headers();self.wfile.write(body);self.close_connection=True

    def _headers_allowed(self,changing=False):
        if self.headers.get_all("Host")!=[self.server.allowed_host]:
            self._reply(403,{"error":"この接続先は許可されていません。"});return False
        origins=self.headers.get_all("Origin",[])
        if (changing and origins!=[self.server.origin]) or (origins and origins!=[self.server.origin]):
            self._reply(403,{"error":"この接続元は許可されていません。"});return False
        if changing:
            values=self.headers.get_all(TOKEN_HEADER,[])
            if len(values)!=1 or not values[0].isascii() or not secrets.compare_digest(values[0],self.server.token):
                self._reply(403,{"error":"画面を再読み込みしてください。"});return False
        return True

    def do_GET(self):
        if not self._headers_allowed():return
        if self.requestline.split()[1]!=self.path:self._reply(404,{"error":"見つかりません。"});return
        if self.path=="/api/state":self._reply(200,self.server.controller.state());return
        paths={"/":("index.html","text/html; charset=utf-8"),"/style.css":("style.css","text/css; charset=utf-8"),"/app.js":("app.js","text/javascript; charset=utf-8")}
        if self.path not in paths:self._reply(404,{"error":"見つかりません。"});return
        name,mime=paths[self.path];data=(STATIC/name).read_bytes()
        if name=="index.html":data=data.replace(b"__MATCH_TOKEN__",self.server.token.encode("ascii"))
        self._reply(200,data,mime)

    def do_POST(self):
        if not self._headers_allowed(changing=True):return
        if self.requestline.split()[1]!=self.path:self._reply(404,{"error":"見つかりません。"});return
        if self.path!="/api/command":self._reply(404,{"error":"見つかりません。"});return
        lengths=self.headers.get_all("Content-Length",[])
        if (self.headers.get_all("Transfer-Encoding") or len(lengths)!=1 or not lengths[0].isascii() or not lengths[0].isdigit()):
            self._reply(400,{"error":"JSON本文の長さを確認してください。"});return
        if len(lengths[0])>5:self._reply(413,{"error":"JSON本文が大きすぎます。"});return
        length=int(lengths[0])
        if length>BODY_LIMIT:self._reply(413,{"error":"JSON本文が大きすぎます。"});return
        if self.headers.get_all("Content-Type")!=["application/json"]:
            self._reply(415,{"error":"JSON形式で送信してください。"});return
        try:
            data=self.rfile.read(length)
            if len(data)!=length:raise ValueError("short body")
            payload=parse_json(data.decode("utf-8"))
        except (ValueError,UnicodeError):self._reply(400,{"error":"JSON本文を確認してください。"});return
        try:self._reply(200,self.server.controller.apply_command(payload))
        except CommandError as error:self._reply(error.status,{"error":str(error)})
        except Exception:self._reply(500,{"error":"処理に失敗しました。試作を再起動してください。"})

    def do_OPTIONS(self):self._reply(405,{"error":"この操作は許可されていません。"})


def create_server(controller=None):return MatchServer(controller)
