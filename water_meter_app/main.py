"""
水表监控工具 - Android 版
基于 Kivy 框架，适配移动端触控
"""
import os
import csv
from datetime import datetime
from kivy.app import App
from kivy.uix.screenmanager import ScreenManager, Screen
from kivy.uix.popup import Popup
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.properties import ObjectProperty, StringProperty
from kivy.clock import Clock
from kivy.utils import platform
from plyer import share

from api_client import ApiClient
from data_source import DataSource
from models import WaterRecord, CustomerDetail


class WaterRecordRow(BoxLayout):
    """自定义列表行，显示水表记录的关键字段"""
    cusName = StringProperty('')
    usersNo = StringProperty('')
    balance = StringProperty('')
    valveState = StringProperty('')
    receiveTime = StringProperty('')

    def on_touch_down(self, touch, *args):
        if self.collide_point(*touch.pos):
            # 通知主屏幕当前行被点击
            app = App.get_running_app()
            main_screen = app.root.get_screen('main')
            # 通过父级 RecycleView 获取数据索引
            parent = self.parent
            while parent and not hasattr(parent, 'data'):
                parent = parent.parent
            if parent and hasattr(parent, 'data'):
                idx = parent.children.index(self) if self in parent.children else -1
                if 0 <= idx < len(parent.data):
                    item = parent.data[idx]
                    main_screen.on_item_click(item)
        return super().on_touch_down(touch, *args)


class MainScreen(Screen):
    rv = ObjectProperty(None)
    days_input = ObjectProperty(None)
    status_spinner = ObjectProperty(None)
    page_input = ObjectProperty(None)
    page_info = ObjectProperty(None)
    status_label = ObjectProperty(None)

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.app = App.get_running_app()
        self.api = self.app.api
        self.ds = self.app.data_source
        self.ds.bind(records=self.on_records_changed)
        self.ds.bind(total=self.on_total_changed)
        self.load_token()
        # 延迟加载第一页
        Clock.schedule_once(lambda dt: self.fetch_data(), 0.5)

    def load_token(self):
        token_file = os.path.join(self.app.user_data_dir, 'token.txt')
        if os.path.exists(token_file):
            try:
                with open(token_file, 'r') as f:
                    token = f.read().strip()
                    self.api.set_token(token)
                    self.update_token_status(True)
            except:
                pass

    def save_token(self, token):
        token_file = os.path.join(self.app.user_data_dir, 'token.txt')
        with open(token_file, 'w') as f:
            f.write(token)
        self.api.set_token(token)
        self.update_token_status(True)

    def update_token_status(self, valid):
        indicator = self.ids.token_indicator
        if valid:
            indicator.text = '●'
            indicator.color = (0, 1, 0, 1)
        else:
            indicator.text = '○'
            indicator.color = (1, 0, 0, 1)

    def show_token_popup(self):
        content = BoxLayout(orientation='vertical', spacing=10, padding=10)
        ti = TextInput(hint_text='输入Token (Bearer xxx 或直接xxx)', multiline=False)
        content.add_widget(ti)
        btn_layout = BoxLayout(size_hint_y=None, height=50, spacing=10)
        btn_ok = Button(text='保存')
        btn_cancel = Button(text='取消')
        btn_layout.add_widget(btn_ok)
        btn_layout.add_widget(btn_cancel)
        content.add_widget(btn_layout)
        popup = Popup(title='设置Token', content=content, size_hint=(0.8, 0.4))
        btn_ok.bind(on_press=lambda x: self._save_token_from_popup(ti.text, popup))
        btn_cancel.bind(on_press=popup.dismiss)
        popup.open()

    def _save_token_from_popup(self, token_text, popup):
        token = token_text.strip()
        if not token:
            self.show_status('Token不能为空', True)
            return
        if not token.lower().startswith('bearer '):
            token = 'Bearer ' + token
        self.save_token(token)
        popup.dismiss()
        self.show_status('Token已保存', False)
        self.fetch_data()

    def fetch_data(self):
        if not self.api.token:
            self.show_status('请先设置Token', True)
            self.show_token_popup()
            return
        days = self.days_input.text or '30'
        status_map = {'启用': '1', '全部': '0', '禁用': '2'}
        enabled = status_map.get(self.status_spinner.text, '1')
        self.ds.set_filters(days, enabled)
        self.ds.fetch_page(1, self._on_fetch_done)

    def _on_fetch_done(self, success, err):
        if success:
            self.show_status(f'加载完成，共 {self.ds.total} 条，第 {self.ds.current_page} 页', False)
        else:
            self.show_status(f'加载失败: {err}', True)

    def on_records_changed(self, instance, records):
        data = []
        for rec in records:
            data.append({
                'cusName': rec.cusName,
                'usersNo': rec.usersNo,
                'waterMeterNo': rec.waterMeterNo,
                'balance': f'{rec.balance:.2f}',
                'valveState': rec.valveState,
                'receiveTime': rec.receiveTime,
                'areaName': rec.areaName,
            })
        self.rv.data = data

    def on_total_changed(self, instance, total):
        max_page = max(1, (total + self.ds.page_size - 1) // self.ds.page_size)
        self.page_info.text = f'共 {max_page} 页 {total} 条'
        self.page_input.text = str(self.ds.current_page)

    def next_page(self):
        self.ds.next_page(self._on_fetch_done)

    def prev_page(self):
        self.ds.prev_page(self._on_fetch_done)

    def jump_page(self):
        try:
            p = int(self.page_input.text)
            self.ds.go_to_page(p, self._on_fetch_done)
        except ValueError:
            self.show_status('请输入有效页码', True)

    def show_status(self, msg, is_error=False):
        self.status_label.text = msg
        self.status_label.color = (1, 0, 0, 1) if is_error else (0, 1, 0, 1)

    def on_item_click(self, item_data):
        users_no = item_data.get('usersNo')
        if not users_no:
            return
        app = App.get_running_app()
        detail_screen = app.root.get_screen('detail')
        detail_screen.load_detail(users_no)
        app.root.current = 'detail'

    def export_csv(self):
        if not self.ds.records:
            self.show_status('没有数据可导出', True)
            return
        temp_dir = self.app.user_data_dir
        filename = f'水表数据_{datetime.now().strftime("%Y%m%d_%H%M%S")}.csv'
        path = os.path.join(temp_dir, filename)
        try:
            with open(path, 'w', newline='', encoding='utf-8-sig') as f:
                writer = csv.writer(f)
                writer.writerow(['户名','户号','水表编号','手机号','地址','区域','余额','表余','上报时间','阀门','水量','IMEI','信号','电压'])
                for rec in self.ds.records:
                    row = [
                        rec.cusName, rec.usersNo, rec.waterMeterNo, rec.telNum,
                        rec.cusAdd, rec.areaName, f'{rec.balance:.2f}',
                        f'{rec.meterBalance:.2f}', rec.receiveTime, rec.valveState,
                        f'{rec.totalPositiveWater:.2f}', rec.imei,
                        rec.signalStrength, rec.batteryVoltage
                    ]
                    writer.writerow(row)
            share.share(text='水表数据导出', file_path=path)
            self.show_status(f'导出成功: {filename}', False)
        except Exception as e:
            self.show_status(f'导出失败: {e}', True)


class DetailScreen(Screen):
    detail_text = StringProperty('')

    def load_detail(self, users_no):
        self.detail_text = '加载中...'
        app = App.get_running_app()
        api = app.api
        def on_success(resp):
            data = resp.get('data')
            if not data:
                self.detail_text = '未找到客户数据'
                return
            if isinstance(data, list) and data:
                customer = CustomerDetail.from_dict(data[0])
            elif isinstance(data, dict):
                customer = CustomerDetail.from_dict(data)
            else:
                self.detail_text = '数据格式错误'
                return
            lines = []
            for k, v in customer.__dict__.items():
                if v is None:
                    v = ''
                elif isinstance(v, float):
                    v = f'{v:.2f}'
                lines.append(f'{k}: {v}')
            self.detail_text = '\n'.join(lines)
        def on_failure(err):
            self.detail_text = f'查询失败: {err}'
        api.get_customer_detail(users_no, on_success, on_failure)


class WaterApp(App):
    def build(self):
        self.api = ApiClient()
        self.data_source = DataSource(self.api)
        sm = ScreenManager()
        sm.add_widget(MainScreen(name='main'))
        sm.add_widget(DetailScreen(name='detail'))
        return sm


if __name__ == '__main__':
    WaterApp().run()