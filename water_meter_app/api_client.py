import json
from kivy.network.urlrequest import UrlRequest
from typing import Callable, Dict, Optional


class ApiClient:
    BASE_URL = "https://cloud.scfxyb.com/api/web"

    def __init__(self, token: str = ""):
        self.token = token
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        }
        if token:
            self.headers["Authorization"] = token

    def set_token(self, token: str):
        self.token = token
        self.headers["Authorization"] = token

    def _request(self, method: str, path: str, on_success: Callable, on_failure: Callable,
                 params: Optional[Dict] = None, data: Optional[Dict] = None):
        url = f"{self.BASE_URL}{path}"
        req_headers = self.headers.copy()
        req = UrlRequest(
            url,
            on_success=lambda req, resp: self._parse_response(resp, on_success, on_failure),
            on_failure=lambda req, err: on_failure(f"网络请求失败: {err}"),
            on_error=lambda req, err: on_failure(f"请求错误: {err}"),
            req_headers=req_headers,
            method=method,
            params=params,
            timeout=30
        )
        if method.upper() == "POST" and data:
            req.data = json.dumps(data).encode('utf-8')
            req.req_headers["Content-Type"] = "application/json"
        # UrlRequest 在 wait() 时会阻塞，但我们不希望阻塞 UI，所以不调用 wait()
        # 回调会异步执行

    def _parse_response(self, resp_data: Dict, on_success: Callable, on_failure: Callable):
        try:
            if resp_data.get("code") != "200":
                raise Exception(resp_data.get("msg", "未知错误"))
            on_success(resp_data)
        except Exception as e:
            on_failure(str(e))

    def get_water_list(self, params: Dict, on_success: Callable, on_failure: Callable):
        self._request("GET", "/device/waterInfoFour/getWaterInfoFourMonitorLst",
                      on_success=on_success, on_failure=on_failure, params=params)

    def get_customer_detail(self, user_no: str, on_success: Callable, on_failure: Callable):
        params = {"key": "2", "value": user_no, "value1": "", "powerOrg": "true"}
        self._request("GET", "/market/customer/order/list",
                      on_success=on_success, on_failure=on_failure, params=params)