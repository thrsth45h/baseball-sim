"""Run the provisional match screen locally. Open the printed URL yourself."""
import sys
from .server import create_server


def main():
    if hasattr(sys.stdout,"reconfigure"):sys.stdout.reconfigure(encoding="utf-8")
    server=create_server()
    print("1試合の操作画面: "+server.origin,flush=True)
    print("終了するには Ctrl+C",flush=True)
    try:server.serve_forever()
    except KeyboardInterrupt:pass
    finally:server.server_close()


if __name__=="__main__":main()
