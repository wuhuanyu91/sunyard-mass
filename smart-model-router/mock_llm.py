# mock_llm.py —— 返回 OpenAI 兼容的 chat/completions（含 SSE）与 embeddings（设计方案附录 F）
import json, hashlib
from http.server import BaseHTTPRequestHandler, HTTPServer

def fake_embedding(text, dim=1024):
    # 确定性伪向量：同文本同向量、不同文本可区分，满足语义缓存命中/未命中两类测试
    h = int.from_bytes(hashlib.sha256(text.encode()).digest(), 'big')
    vec = [((h >> (i % 224)) & 0xFF) / 255.0 for i in range(dim)]
    n = sum(x * x for x in vec) ** 0.5 or 1.0
    return [round(x / n, 6) for x in vec]

class H(BaseHTTPRequestHandler):
    def do_POST(self):
        n = int(self.headers.get('Content-Length', 0))
        body = json.loads(self.rfile.read(n) or b'{}')
        if self.path.endswith('/v1/embeddings'):
            text = body.get('input', '')
            if isinstance(text, list): text = text[0] if text else ''
            self.send_response(200); self.send_header('Content-Type', 'application/json'); self.end_headers()
            self.wfile.write(json.dumps({"data":[{"embedding":fake_embedding(text),"index":0}],
                                         "usage":{"prompt_tokens":5,"total_tokens":5}}).encode())
            return
        stream = body.get('stream', False)
        if stream:
            self.send_response(200); self.send_header('Content-Type', 'text/event-stream'); self.end_headers()
            for w in ["你好", "，", "这是", "Mock", "回复"]:
                self.wfile.write(f"data: {json.dumps({'choices':[{'delta':{'content':w}}]})}\n\n".encode())
            self.wfile.write(b"data: [DONE]\n\n")
        else:
            self.send_response(200); self.send_header('Content-Type', 'application/json'); self.end_headers()
            self.wfile.write(json.dumps({"choices":[{"message":{"role":"assistant","content":"Mock 回复"}}],
                                         "usage":{"prompt_tokens":5,"completion_tokens":5,"total_tokens":10}}).encode())
    def log_message(self, *a): pass

HTTPServer(('0.0.0.0', 11434), H).serve_forever()
