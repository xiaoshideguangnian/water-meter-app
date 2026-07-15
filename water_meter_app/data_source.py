from kivy.event import EventDispatcher
from kivy.properties import ListProperty, NumericProperty, StringProperty
from models import WaterRecord
from api_client import ApiClient
from typing import Dict, Optional


class DataSource(EventDispatcher):
    records = ListProperty([])
    total = NumericProperty(0)
    current_page = NumericProperty(1)
    page_size = NumericProperty(100)
    not_report_days = StringProperty("30")
    enabled = StringProperty("1")

    def __init__(self, api_client: ApiClient):
        super().__init__()
        self.api = api_client
        self._col_filters: Dict[str, str] = {}
        self._all_records: list = []

    def set_filters(self, not_report_days: str, enabled: str, col_filters: Optional[Dict[str, str]] = None):
        self.not_report_days = not_report_days
        self.enabled = enabled
        if col_filters is not None:
            self._col_filters = col_filters

    def fetch_page(self, page: int, callback=None):
        params = {
            "current": str(page),
            "size": str(self.page_size),
            "key": "1",
            "waterMeterNo": "",
            "notReportDays": self.not_report_days,
            "enabled": self.enabled
        }

        def on_success(resp):
            data = resp.get("data", {})
            raw = data.get("records", [])
            total = data.get("total", 0)
            self.total = total
            self.current_page = page
            self._all_records = [WaterRecord.from_dict(r) for r in raw]
            self._apply_col_filters()
            if callback:
                callback(True, None)

        def on_failure(err):
            if callback:
                callback(False, err)

        self.api.get_water_list(params, on_success, on_failure)

    def _apply_col_filters(self):
        if not self._col_filters:
            self.records = self._all_records[:]
            return
        filtered = []
        for rec in self._all_records:
            match = True
            for col_id, keyword in self._col_filters.items():
                val = getattr(rec, col_id, "")
                if val is None:
                    cell = ""
                elif isinstance(val, float):
                    cell = f"{val:.2f}"
                else:
                    cell = str(val)
                if keyword.lower() not in cell.lower():
                    match = False
                    break
            if match:
                filtered.append(rec)
        self.records = filtered

    def set_col_filter(self, col_id: str, keyword: str):
        if not keyword:
            self._col_filters.pop(col_id, None)
        else:
            self._col_filters[col_id] = keyword
        self._apply_col_filters()

    def clear_col_filters(self):
        self._col_filters.clear()
        self._apply_col_filters()

    def next_page(self, callback=None):
        if self.current_page * self.page_size < self.total:
            self.fetch_page(self.current_page + 1, callback)

    def prev_page(self, callback=None):
        if self.current_page > 1:
            self.fetch_page(self.current_page - 1, callback)

    def go_to_page(self, page: int, callback=None):
        max_page = max(1, (self.total + self.page_size - 1) // self.page_size)
        page = max(1, min(page, max_page))
        if page != self.current_page:
            self.fetch_page(page, callback)

    def refresh(self, callback=None):
        self.fetch_page(self.current_page, callback)