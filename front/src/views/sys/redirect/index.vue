<template>
  <div></div>
</template>
<script lang="ts" setup>
  import { unref } from 'vue';
  import { useRouter } from 'vue-router';

  const { currentRoute, replace } = useRouter();

  const { params, query } = unref(currentRoute);
  const { path, _redirect_type = 'path', query: _p_query } = params;

  Reflect.deleteProperty(params, '_redirect_type');
  Reflect.deleteProperty(params, 'path');
  let _query = query;
  if (_p_query) {
    _query = JSON.parse(_p_query);
    Reflect.deleteProperty(params, 'query');
  }
  const _path = Array.isArray(path) ? path.join('/') : path;
  console.log('params, query', { path, _redirect_type, _path });
  if (_redirect_type === 'name') {
    replace({
      name: _path,
      query: _query,
      params: JSON.parse((params._origin_params as string) ?? '{}'),
    });
  } else {
    replace({
      path: _path.startsWith('/') ? _path : '/' + _path,
      query: _query,
    });
  }
</script>
