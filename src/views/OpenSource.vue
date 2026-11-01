<template>
  <div class="page page--plain">
    <BackBar title="开源许可" />

    <section class="card intro">
      <h2 class="intro-title">开源组件许可</h2>
      <p class="intro-text">
        本应用使用了以下开源组件。各组件的版权归原作者所有，使用遵循其各自的许可证。以下声明用于满足许可证的署名与许可文本保留要求。
      </p>
    </section>

    <section v-for="group in groups" :key="group.id" class="card block">
      <h2 class="block-title">{{ group.title }}</h2>
      <p class="license-name">{{ group.license }}</p>
      <ul class="component-list">
        <li v-for="item in group.items" :key="item.name">
          <span class="component-name">{{ item.name }}</span>
          <span class="component-copy">{{ item.copyright }}</span>
        </li>
      </ul>
    </section>

    <section class="card block">
      <h2 class="block-title">MIT License</h2>
      <pre class="license-text">{{ MIT_TEXT }}</pre>
    </section>

    <section class="card block">
      <h2 class="block-title">Apache License 2.0</h2>
      <p class="block-text">
        以下组件按 Apache License 2.0 授权。完整许可证文本可在
        <span class="url">https://www.apache.org/licenses/LICENSE-2.0</span>
        查看。
      </p>
      <pre class="license-text">{{ APACHE_NOTICE }}</pre>
    </section>
  </div>
</template>

<script setup>
import BackBar from '../components/BackBar.vue'

const MIT_TEXT = `MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.`

const APACHE_NOTICE = `Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.`

const groups = [
  {
    id: 'web',
    title: '前端运行时',
    license: 'MIT License',
    items: [
      { name: 'Vue 3', copyright: 'Copyright (c) 2018-present Yuxi (Evan) You and Vue contributors' },
      { name: 'Pinia', copyright: 'Copyright (c) 2019-present Eduardo San Martin Morote' },
      { name: 'Vue Router', copyright: 'Copyright (c) 2014-present Evan You' }
    ]
  },
  {
    id: 'capacitor',
    title: 'Capacitor',
    license: 'MIT License',
    items: [
      { name: '@capacitor/core / android / app / status-bar', copyright: 'Copyright (c) 2017-present Drifty Co.' }
    ]
  },
  {
    id: 'android',
    title: 'Android / AndroidX',
    license: 'Apache License 2.0',
    items: [
      { name: 'AndroidX AppCompat / Core / CoordinatorLayout / Splashscreen / WebKit / Activity', copyright: 'Copyright (c) The Android Open Source Project' },
      { name: 'OkHttp', copyright: 'Copyright (c) Square, Inc.' }
    ]
  }
]
</script>

<style scoped>
.intro {
  padding: 18px 16px;
}

.intro-title {
  font-size: 18px;
  font-weight: 600;
}

.intro-text {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.75;
  color: var(--text-secondary);
  text-align: justify;
}

.block {
  margin-top: 12px;
  padding: 16px;
}

.block-title {
  font-size: 15px;
  font-weight: 600;
}

.license-name {
  margin-top: 4px;
  font-size: 12px;
  color: var(--brand);
}

.component-list {
  margin-top: 10px;
  list-style: none;
}

.component-list li {
  padding: 9px 0;
  border-bottom: 1px solid var(--border);
}

.component-list li:last-child {
  border-bottom: 0;
}

.component-name {
  display: block;
  font-size: 13px;
  color: var(--text);
}

.component-copy {
  display: block;
  margin-top: 3px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--text-tertiary);
}

.block-text {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-secondary);
}

.url {
  color: var(--brand);
  word-break: break-all;
}

.license-text {
  margin-top: 10px;
  padding: 12px;
  border-radius: 10px;
  background: var(--card-muted);
  color: var(--text-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
