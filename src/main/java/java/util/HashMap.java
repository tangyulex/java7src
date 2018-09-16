package java.util;
import java.io.*;

public class HashMap<K,V>
    extends AbstractMap<K,V>
    implements Map<K,V>, Cloneable, Serializable
{
    /**
     * 默认的初始容量
     */
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // aka 16

    /**
     * 最大容量
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * 默认的加载因子
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * 空数组
     */
    static final Entry<?,?>[] EMPTY_TABLE = {};

    /**
     * 根据需要调整大小，其大小必须是2的幂
     */
    transient Entry<K,V>[] table = (Entry<K,V>[]) EMPTY_TABLE;

    /**
     * 键值对的数量
     */
    transient int size;

    /**
     * 当在map中加入某一个值之后，map的长度等于该值，则进行map大小的调整，它等于 capacity * load factor
     */
    int threshold;

    /**
     * 加载因子
     */
    final float loadFactor;

    /**
     * 记录这个HashMap在结构上被修改的次数，结构上的修改是指那些改变HashMap中映射的数量或者以其他方式修改其内部结构（例如，重新哈希）的次数。
     * 此字段用于使哈希映射的集合视图上的迭代器快速失效。（参见ConcurrentModificationException异常）。
     */
    transient int modCount;

    /**
     * map容量的阈值的默认值，当key使用String类型时，为了减少由于字符串键的弱散列代码计算而导致的冲突的发生率，可以通过设置系统属性（jdk.map.althashing.threshold）来重写该值
     * 1表示总是使用替代hash，-1表示永远不需要替代hash
     */
    static final int ALTERNATIVE_HASHING_THRESHOLD_DEFAULT = Integer.MAX_VALUE;

    /**
     * 静态内部类Holder，存放一些只能在虚拟机启动后才能初始化的值
     */
    private static class Holder {

        /**
         * 容量阈值，初始化hashSeed的时候会用到该值
         */
        static final int ALTERNATIVE_HASHING_THRESHOLD;

        static {
            // 获取系统变量 jdk.map.althashing.threshold
            String altThreshold = java.security.AccessController.doPrivileged(
                new sun.security.action.GetPropertyAction(
                    "jdk.map.althashing.threshold"));

            int threshold;
            try {
                threshold = (null != altThreshold)
                        ? Integer.parseInt(altThreshold)
                        : ALTERNATIVE_HASHING_THRESHOLD_DEFAULT;

                // 禁用替代hash
                if (threshold == -1) {
                    threshold = Integer.MAX_VALUE;
                }

                if (threshold < 0) {
                    throw new IllegalArgumentException("value must be positive integer.");
                }
            } catch(IllegalArgumentException failed) {
                throw new Error("Illegal value for 'jdk.map.althashing.threshold'", failed);
            }

            ALTERNATIVE_HASHING_THRESHOLD = threshold;
        }
    }

    /**
     * hash种子，主要为了降低hash冲突概率，0表示禁用替代hash，在做hash的时候会用到
     */
    transient int hashSeed = 0;

    /**
     * 构造一个指定容量和加载因子的空HashMap，当这个两个值为负时会抛出异常
     */
    public HashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal initial capacity: " +
                                               initialCapacity);
        // 当初始化的容量超过最大容量（1<<30），则将初始化容量设置为最大容量
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal load factor: " +
                                               loadFactor);

        // 初始化加载因子
        this.loadFactor = loadFactor;
        // 初始化阈值为输入的容量大小
        threshold = initialCapacity;

        // 模板方法
        init();
    }

    /**
     * 初始化指定容量和加载因子为0.75的空HashMap
     * @see #HashMap(int, float)
     */
    public HashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * 初始化容量为16和加载因子为0.75的空HashMap
     * @see #HashMap(int, float)
     */
    public HashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /**
     * 通过输入的Map（m）构造一个加载因子为0.75并且有充足容量的HashMap，然后将m中的键值放入新构造的HashMap
     */
    public HashMap(Map<? extends K, ? extends V> m) {
        // 构造出来的HashMap加载因子为0.75，容量在(m.size/0.75)+1和16之间取一个最大值，即保证容量至少为16
        this(Math.max((int) (m.size() / DEFAULT_LOAD_FACTOR) + 1,
                      DEFAULT_INITIAL_CAPACITY), DEFAULT_LOAD_FACTOR);

        // 此时threshold为capacity
        inflateTable(threshold);

        // 将m中的所有键值放入当前HashMap
        putAllForCreate(m);
    }

    /**
     * 返回大于等于number的2的幂次方，输入超过最大容量的话就返回最大容量
     */
    private static int roundUpToPowerOf2(int number) {
        // assert number >= 0 : "number must be non-negative";
        return number >= MAXIMUM_CAPACITY
                ? MAXIMUM_CAPACITY
                : (number > 1) ? Integer.highestOneBit((number - 1) << 1) : 1;
    }

    /**
     * 根据toSize计算出一个容量，作为table的length
     */
    private void inflateTable(int toSize) {
        // 将toSize调整为大于等于toSize的2的幂次方作为table的容量
        int capacity = roundUpToPowerOf2(toSize);
        // 将阈值调整为容量*加载因子
        threshold = (int) Math.min(capacity * loadFactor, MAXIMUM_CAPACITY + 1);
        // 初始化table，给定数组大小为capacity
        table = new Entry[capacity];
        // 初始化hash种子
        initHashSeedAsNeeded(capacity);
    }

    // internal utilities

    /**
     * 所有构造器包括伪构造器都会调用此方法，由子类重写
     */
    void init() {
    }

    /**
     * 根据capacity初始化hash种子，当需要的时候再调用此方法
     */
    final boolean initHashSeedAsNeeded(int capacity) {
        // 当前hash种子不为0
        boolean currentAltHashing = hashSeed != 0;
        // 容量大于给定使用替代hash的阈值
        boolean useAltHashing = sun.misc.VM.isBooted() &&
                (capacity >= Holder.ALTERNATIVE_HASHING_THRESHOLD);
        boolean switching = currentAltHashing ^ useAltHashing;
        if (switching) {
            // 如果使用替代hash，则算一个hash种子出来，否则将hash种子设置为0
            hashSeed = useAltHashing
                ? sun.misc.Hashing.randomHashSeed(this)
                : 0;
        }

        // 如果改变过hash种子的值则返回true
        return switching;
    }

    /**
     * 获得key自身的hashCode，然后进行加工，这么做是为了防止key的hash函数质量太低，
     * HashMap使用长度为2的幂次方hash表，否则会遇到hash冲突，而这些hash值在较低位上不同。
     * key为null时，不需要调用此函数，hashCode总是为0，即在hash表的第0个位置
     *
     * @see java.util.HashMap#putForNullKey(java.lang.Object)
     * @see java.util.HashMap#getForNullKey()
     */
    final int hash(Object k) {
        int h = hashSeed;
        if (0 != h && k instanceof String) {
            return sun.misc.Hashing.stringHash32((String) k);
        }

        h ^= k.hashCode();

        // 此函数确保仅在每个位的位置上以常数倍数相差的hash值，具有有限数量的冲突（在默认负载因子下约为8）。
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    /**
     * 根据输入的hashCode和table长度，计算出该hashCode应该处于table的哪个位置
     */
    static int indexFor(int h, int length) {
        // assert Integer.bitCount(length) == 1 : "length必须是非0的并且是2的幂次方";
        return h & (length-1);
    }

    /**
     * 获取table中实际包含的Entry数量
     */
    public int size() {
        return size;
    }

    /**
     * 返回table中是否不存在Entry
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 返回指定key映射到的值，如果没有映射到的话就返回null，
     * 返回值为null不表示hash表中不包含这个key的映射，也可能是value本身就是null，
     * 可以使用{@link #containsKey containsKey}来判断是否包含某个值
     */
    public V get(Object key) {
        // 如果key为null时，取table[0]中key为null的Entry，因为null的hash值为0
        if (key == null)
            return getForNullKey();

        // 通过key在hash表中找到Entry
        Entry<K,V> entry = getEntry(key);

        // 没有输入key对应的Entry时返回null，否则返回Entry的value值
        return null == entry ? null : entry.getValue();
    }

    /**
     * 获取key为null的value值
     */
    private V getForNullKey() {
        if (size == 0) {
            return null;
        }
        // 取table[0]中key为null的Entry
        for (Entry<K,V> e = table[0]; e != null; e = e.next) {
            if (e.key == null)
                return e.value;
        }
        return null;
    }

    /**
     * 返回hash表中是否包含指定key的映射
     */
    public boolean containsKey(Object key) {
        return getEntry(key) != null;
    }

    /**
     * 返回与指定key关联的Entry，返回null则表示不存在指定key的映射
     */
    final Entry<K,V> getEntry(Object key) {
        // 空hash表直接返回null
        if (size == 0) {
            return null;
        }

        // 获取当前key的被二次加工的hash值
        int hash = (key == null) ? 0 : hash(key);

        // 找到该key在table中的桶位，并遍历该桶下的链表
        for (Entry<K,V> e = table[indexFor(hash, table.length)];
             e != null;
             e = e.next) {
            Object k;
            // 如果e的hashCode等于当前要查找的key的hashCode（hash都不一样就直接过了，效率高一点），
            // 然后比较key是否与e的key是同一个对象（==判断的效率更高一点，如果相等话就没必要判断效率更低的equals了），
            // 如果不是同一个对象则检查两者是否equals，
            // 如果是同一个对象或者是两个key相互equals的话，则返回这个Entry
            if (e.hash == hash &&
                ((k = e.key) == key || (key != null && key.equals(k))))
                return e;
        }

        // 找不到指定key的映射
        return null;
    }

    /**
     * 将指定的键值对关联起来放进hash表中，如果之前存在过这个key，那么返回该key原来映射到的value
     */
    public V put(K key, V value) {
        if (table == EMPTY_TABLE) {
            inflateTable(threshold);
        }
        if (key == null)
            return putForNullKey(value);
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        for (Entry<K,V> e = table[i]; e != null; e = e.next) {
            Object k;
            if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
                V oldValue = e.value;
                e.value = value;
                e.recordAccess(this);
                return oldValue;
            }
        }

        modCount++;
        addEntry(hash, key, value, i);
        return null;
    }

    /**
     * 替换key为null的value值，并返回旧的value值
     */
    private V putForNullKey(V value) {
        // 将table[0]下key为null的值替换为输入的value
        for (Entry<K,V> e = table[0]; e != null; e = e.next) {
            if (e.key == null) {
                V oldValue = e.value;
                e.value = value;
                // 回调Entry的recordAccess，当其value值发生变化时
                e.recordAccess(this);
                return oldValue;
            }
        }
        // 记录一下hash表被改变过了
        modCount++;
        // 在table[0]下的链表挂一个key为null的Entry
        addEntry(0, null, value, 0);
        return null;
    }

    /**
     * 此方法被用于通过那些伪构造器（clone、readObject）创建HashMap，它不会关心table大小的问题
     */
    private void putForCreate(K key, V value) {
        // 对key做hash，获取hash值
        int hash = null == key ? 0 : hash(key);
        // 根据hash值和table的长度计算对应的位置
        int i = indexFor(hash, table.length);

        /**
         * 这里会查找先前存在的key，克隆或反序列时不会关心这个操作的，
         * 如果输入的是一个StoredMap（通过equals来保证排序），那么这个操作只会发生于构造时
         */
        // 遍历table某一个位置（i）下链表中的每个元素（e）
        for (Entry<K,V> e = table[i]; e != null; e = e.next) {
            Object k;
            // 如果e的hashCode等于当前要加入的key的hashCode（hash都不一样就直接过了，效率高一点），
            // 然后比较key是否与e的key是同一个对象（==判断的效率更高一点，如果相等话就没必要判断效率更低的equals了），
            // 如果不是同一个对象则检查两者是否equals，
            // 如果是同一个对象或者是两个key相互equals的话，则直接替换掉key所对应的value
            if (e.hash == hash &&
                ((k = e.key) == key || (key != null && key.equals(k)))) {
                e.value = value;
                return;
            }
        }

        // 如果输入的key之前不存在，则创建一个Entry放在table的对应位置（i）的链表结构中
        createEntry(hash, key, value, i);
    }

    /**
     * 将输入的map中的键值放入当前HashMap，此方法用于创建HashMap时使用，不会关心扩容问题
     */
    private void putAllForCreate(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            putForCreate(e.getKey(), e.getValue());
    }

    /**
     * 调整数组到一个更大的容量，并将原数组中的数据重新hash到新的数组中，
     * 如果已经到最大容量了，那么此方法不会再次调整容量，
     * 并且会将调整容量大小的阈值设置为Integer.MAX_VALUE，以防止此方法在未来再次被调用
     *
     * @param newCapacity 输入的容量必须是2的幂次方，并且必须大于当前容量
     */
    void resize(int newCapacity) {
        Entry[] oldTable = table;
        int oldCapacity = oldTable.length;
        // 如果当前数组已经到了最大容量，就不再调整容量大小了，并且将阈值调整为int最大值，以防止此方法未来再次被调用
        if (oldCapacity == MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }
        // 创建一个指定容量的新数组
        Entry[] newTable = new Entry[newCapacity];
        // 将当前数组中的元素重新hash到新的数组中，并将table指向该数组
        transfer(newTable, initHashSeedAsNeeded(newCapacity));
        table = newTable;
        // 修改阈值大小为当前容量*加载因子
        threshold = (int)Math.min(newCapacity * loadFactor, MAXIMUM_CAPACITY + 1);
    }

    /**
     * 将旧table中的元素全部转移到新table中
     */
    void transfer(Entry[] newTable, boolean rehash) {
        int newCapacity = newTable.length;
        for (Entry<K,V> e : table) { // 外层循环，遍历table的每个桶位
            while(null != e) { // 内层循环，遍历外层循环对应桶位下的链表
                // 获取e所在链表的下一个Entry，作为内层循环要处理的下一个Entry
                Entry<K,V> next = e.next;
                // 如果需要的话，重新计算hash值
                if (rehash) {
                    e.hash = null == e.key ? 0 : hash(e.key);
                }
                // 找到e的hash值在新table中对应的位置
                int i = indexFor(e.hash, newCapacity);
                // 将e.next指向新table对应位置的新链表的首位
                e.next = newTable[i];
                // 新table对应位置放上当前内层循环遍历到的Entry
                newTable[i] = e;
                // 将e赋值为原链表的下一个元素。下一次内层循环将此元素分配到新table中
                e = next;
            }
        }
    }

    /**
     * 将指定map中的元素copy到此map中，如果当前map中存在与指定map中相同的key会替换掉当前map中的映射
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        int numKeysToBeAdded = m.size();
        if (numKeysToBeAdded == 0)
            return;

        if (table == EMPTY_TABLE) {
            inflateTable((int) Math.max(numKeysToBeAdded * loadFactor, threshold));
        }

        // 如果输入的map的大小大于当前阈值，则先扩好当前map的容量
        if (numKeysToBeAdded > threshold) {
            // 计算出目标容量，如果大于最大容量的话就取最大容量
            int targetCapacity = (int)(numKeysToBeAdded / loadFactor + 1);
            if (targetCapacity > MAXIMUM_CAPACITY)
                targetCapacity = MAXIMUM_CAPACITY;
            int newCapacity = table.length;
            // 每次将newCapacity增加一倍，直到其值大于目标容量
            while (newCapacity < targetCapacity)
                newCapacity <<= 1;
            // 对当前map进行扩容
            if (newCapacity > table.length)
                resize(newCapacity);
        }

        // 遍历输入的map，将数据放到当前map中
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            put(e.getKey(), e.getValue());
    }

    /**
     * 将当前map中指定key的Entry移除掉，并返回其对应的value值，没有这个key会返回null，或者本身value为null也是返回null
     */
    public V remove(Object key) {
        Entry<K,V> e = removeEntryForKey(key);
        return (e == null ? null : e.value);
    }

    /**
     * 移除指定key对应的Entry，如果返回null，说明没有这个key的映射
     */
    final Entry<K,V> removeEntryForKey(Object key) {
        if (size == 0) {
            return null;
        }
        // 计算出要移除的Entry在table中的桶位
        int hash = (key == null) ? 0 : hash(key);
        int i = indexFor(hash, table.length);

        // 遍历指定桶位下的链表，e代表当前遍历到的元素，prev代表当前遍历到的元素的上一个元素
        Entry<K,V> prev = table[i];
        Entry<K,V> e = prev;
        while (e != null) {
            Entry<K,V> next = e.next;
            Object k;
            // 如果当前Entry的key为指定的key
            if (e.hash == hash &&
                ((k = e.key) == key || (key != null && key.equals(k)))) {
                // 记录map中有数据进出
                modCount++;
                // map的size减1
                size--;
                // 如果当前移除的是链表头
                if (prev == e)
                    // 则将其下一个元素作为链表头
                    table[i] = next;
                else
                    // 否则将当前要移除的Entry的上一个Entry的next指向当前要移除的Entry的下一个元素
                    prev.next = next;
                // 通知当前这个被移出map的Entry
                e.recordRemoval(this);
                // 返回被移除的Entry
                return e;
            }
            // 将prev指向当前元素，e指向下一个元素
            prev = e;
            e = next;
        }

        // 找不到指定key，返回null
        return e;
    }

    /**
     * 移除指定的Entry
     */
    final Entry<K,V> removeMapping(Object o) {
        if (size == 0 || !(o instanceof Map.Entry))
            return null;

        Map.Entry<K,V> entry = (Map.Entry<K,V>) o;
        // 计算出要移除的Entry在table中的桶位
        Object key = entry.getKey();
        int hash = (key == null) ? 0 : hash(key);
        int i = indexFor(hash, table.length);
        // 遍历指定桶位下的链表，e代表当前遍历到的元素，prev代表当前遍历到的元素的上一个元素
        Entry<K,V> prev = table[i];
        Entry<K,V> e = prev;
        while (e != null) {
            Entry<K,V> next = e.next;
            if (e.hash == hash && e.equals(entry)) {
                // 记录map中有数据进出
                modCount++;
                // map的size减1
                size--;
                // 如果当前移除的是链表头
                if (prev == e)
                    // 则将其下一个元素作为链表头
                    table[i] = next;
                else
                    // 否则将当前要移除的Entry的上一个Entry的next指向当前要移除的Entry的下一个元素
                    prev.next = next;
                // 通知当前这个被移出map的Entry
                e.recordRemoval(this);
                // 返回被移除的Entry
                return e;
            }
            // 将prev指向当前元素，e指向下一个元素
            prev = e;
            e = next;
        }

        // 找不到指定key，返回null
        return e;
    }

    /**
     * 移除当前map中的所有元素
     */
    public void clear() {
        // 记录当前map中有数据进出
        modCount++;
        // 将table中的数据全部赋值为null
        Arrays.fill(table, null);
        // 大小为0
        size = 0;
    }

    /**
     * 如果当前map中存在某一个或几个Entry的value为指定value则返回true
     */
    public boolean containsValue(Object value) {
        // value为null单独处理，HashMap是允许key或value为null的
        if (value == null)
            return containsNullValue();

        // 遍历所有元素，如果存在该value则返回true
        Entry[] tab = table;
        for (int i = 0; i < tab.length ; i++)
            for (Entry e = tab[i] ; e != null ; e = e.next)
                if (value.equals(e.value))
                    return true;
        return false;
    }

    /**
     * 判断当前Map中是否包含value为null的Entry
     */
    private boolean containsNullValue() {
        Entry[] tab = table;
        for (int i = 0; i < tab.length ; i++)
            for (Entry e = tab[i] ; e != null ; e = e.next)
                if (e.value == null)
                    return true;
        return false;
    }

    /**
     * 浅克隆当前map，注意map中的键值是不会被克隆的
     */
    public Object clone() {
        HashMap<K,V> result = null;
        try {
            result = (HashMap<K,V>)super.clone();
        } catch (CloneNotSupportedException e) {
            // assert false;
        }
        // 现将table扩容好
        if (result.table != EMPTY_TABLE) {
            result.inflateTable(Math.min(
                (int) Math.min(
                    size * Math.min(1 / loadFactor, 4.0f),
                    // we have limits...
                    HashMap.MAXIMUM_CAPACITY),
               table.length));
        }
        result.entrySet = null;
        result.modCount = 0;
        result.size = 0;
        result.init();
        // 将当前map中的元素copy到result中
        result.putAllForCreate(this);
        return result;
    }

    /**
     * 作为HashMap中的一个元素
     */
    static class Entry<K,V> implements Map.Entry<K,V> {
        // 键
        final K key;
        // 值
        V value;
        // 下一个Entry
        Entry<K,V> next;
        // 当前key的hash值（避免总是动态计算）
        int hash;

        /**
         * Creates new entry.
         */
        Entry(int h, K k, V v, Entry<K,V> n) {
            value = v;
            next = n;
            key = k;
            hash = h;
        }

        public final K getKey() {
            return key;
        }

        public final V getValue() {
            return value;
        }

        public final V setValue(V newValue) {
            V oldValue = value;
            value = newValue;
            return oldValue;
        }

        // 键值相同就认为相等
        public final boolean equals(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry e = (Map.Entry)o;
            Object k1 = getKey();
            Object k2 = e.getKey();
            if (k1 == k2 || (k1 != null && k1.equals(k2))) {
                Object v1 = getValue();
                Object v2 = e.getValue();
                if (v1 == v2 || (v1 != null && v1.equals(v2)))
                    return true;
            }
            return false;
        }

        // 根据键值计算出的hash值（符合 a equals b 必须保证 a.hashCode == b.hashCode 的原则）
        public final int hashCode() {
            return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
        }

        public final String toString() {
            return getKey() + "=" + getValue();
        }

        /**
         * 当value发生变化时，调用此方法通知Entry
         */
        void recordAccess(HashMap<K,V> m) {
        }

        /**
         * 当Entry要移出hash表时，调用此方法通知Entry
         */
        void recordRemoval(HashMap<K,V> m) {
        }
    }

    /**
     * 添加新的Entry到当前Map中，并且负责扩容
     */
    void addEntry(int hash, K key, V value, int bucketIndex) {
        // 如果当前map中的Entry数量大于等于扩容阈值，并且发生hash冲突时进行扩容（暂时不冲突就暂时不扩容，扩容也是为了解决hash冲突的问题）
        if ((size >= threshold) && (null != table[bucketIndex])) {
            // 容量为原来的两倍
            resize(2 * table.length);
            // 重新计算hash值和桶位
            hash = (null != key) ? hash(key) : 0;
            bucketIndex = indexFor(hash, table.length);
        }

        // 创建一个新的Entry加入当前Map
        createEntry(hash, key, value, bucketIndex);
    }

    /**
     * 和addEntry差不多，但它是当创建entry时使用的（构造器或伪构造器），
     * 此方法不需要关心重新调整table大小，子类可以重写此方法以改变行为
     */
    void createEntry(int hash, K key, V value, int bucketIndex) {
        // 创建一个entry放在table的指定桶位（bucketIndex）
        Entry<K,V> e = table[bucketIndex];
        // 将新加入的Entry放在桶位对应链表的第一个位置
        table[bucketIndex] = new Entry<>(hash, key, value, e);
        // 每次新增entry时都会使size+1
        size++;
    }

    /**
     * 当前map的迭代器实现
     */
    private abstract class HashIterator<E> implements Iterator<E> {
        // 下一个Entry
        Entry<K,V> next;        // next entry to return
        // 迭代期间不允许有数据进出HashMap,为了快速失败
        int expectedModCount;   // For fast-fail
        // 当前迭代到的下标
        int index;              // current slot
        // 当前迭代到的元素
        Entry<K,V> current;     // current entry

        HashIterator() {
            // 记录创建迭代器时的modCount，迭代期间如果此modCount发生改变将快速失败
            expectedModCount = modCount;
            if (size > 0) { // advance to first entry
                Entry[] t = table;
                // 将下一个不为null的Entry链表头赋值给next，其下标+1赋值给index
                while (index < t.length && (next = t[index++]) == null)
                    ;
            }
        }

        /**
         * 判断是否存在下一个Entry
         */
        public final boolean hasNext() {
            return next != null;
        }

        /**
         * 获取下一个Entry
         */
        final Entry<K,V> nextEntry() {
            // 不允许在迭代期间map中有数据进出，否则抛出并发更改异常
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();

            // 如果不存在下一个Entry则直接抛出异常
            Entry<K,V> e = next;
            if (e == null)
                throw new NoSuchElementException();

            // 将下一个Entry设置为当前next所在链表的下一个Entry
            if ((next = e.next) == null) {
                Entry[] t = table;
                // 如果已经到了当前桶位的链表尾部，则将下一个不为null的Entry链表头赋值给next，其下标+1赋值给index
                while (index < t.length && (next = t[index++]) == null)
                    ;
            }
            // 记录并返回当前的下一个元素
            current = e;
            return e;
        }

        /**
         * 移除迭代器当前迭代到的Entry
         */
        public void remove() {
            // 如果当前没有迭代到Entry或者当前Entry已经移除，则抛出异常
            if (current == null)
                throw new IllegalStateException();
            // 迭代期间不允许有数据进出，否则抛出并发更改异常
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            Object k = current.key;
            // 将当前值设置为null，表示当前已经移除
            current = null;
            // 从map中移除该key关联的Entry
            HashMap.this.removeEntryForKey(k);
            // 将期望map中数据进出的数量，更改为当前map数据进出的数量
            expectedModCount = modCount;
        }
    }

    /**
     * value迭代器
     */
    private final class ValueIterator extends HashIterator<V> {
        public V next() {
            return nextEntry().value;
        }
    }

    /**
     * key迭代器
     */
    private final class KeyIterator extends HashIterator<K> {
        public K next() {
            return nextEntry().getKey();
        }
    }

    /**
     * Entry迭代器
     */
    private final class EntryIterator extends HashIterator<Map.Entry<K,V>> {
        public Map.Entry<K,V> next() {
            return nextEntry();
        }
    }

    // Subclass overrides these to alter behavior of views' iterator() method
    Iterator<K> newKeyIterator()   {
        return new KeyIterator();
    }
    Iterator<V> newValueIterator()   {
        return new ValueIterator();
    }
    Iterator<Map.Entry<K,V>> newEntryIterator()   {
        return new EntryIterator();
    }


    // Views

    private transient Set<Map.Entry<K,V>> entrySet = null;

    /**
     * 返回当前map中所有key的视图，由于这个视图是由当前map支撑的，所以map中的数据改变，这个视图也会改变，反之亦然。
     * 支持remove、removeAll、retainAll、clear等操作，不支持add、addAll等操作
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        return (ks != null ? ks : (keySet = new KeySet()));
    }

    /**
     * 代表当前Map中所有key的视图
     */
    private final class KeySet extends AbstractSet<K> {
        public Iterator<K> iterator() {
            return newKeyIterator();
        }
        public int size() {
            return size;
        }
        public boolean contains(Object o) {
            return containsKey(o);
        }
        public boolean remove(Object o) {
            return HashMap.this.removeEntryForKey(o) != null;
        }
        public void clear() {
            HashMap.this.clear();
        }
    }

    /**
     * 返回当前map中所有value的视图，由于这个视图是由当前map支撑的，所以map中的数据改变，这个视图也会改变，反之亦然。
     * 支持remove、removeAll、retainAll、clear等操作，不支持add、addAll等操作
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        return (vs != null ? vs : (values = new Values()));
    }

    /**
     * 代表当前Map中所有value的视图
     */
    private final class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return newValueIterator();
        }
        public int size() {
            return size;
        }
        public boolean contains(Object o) {
            return containsValue(o);
        }
        public void clear() {
            HashMap.this.clear();
        }
    }

    /**
     * 返回当前map中所有Entry的视图，由于这个视图是由当前map支撑的，所以map中数据改变，这个视图也会改变，反之亦然。
     * 支持remove、removeAll、retainAll、clear等操作，不支持add、addAll等操作
     */
    public Set<Map.Entry<K,V>> entrySet() {
        return entrySet0();
    }

    private Set<Map.Entry<K,V>> entrySet0() {
        Set<Map.Entry<K,V>> es = entrySet;
        return es != null ? es : (entrySet = new EntrySet());
    }

    /**
     * 代表当前Map中所有Entry的视图
     */
    private final class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public Iterator<Map.Entry<K,V>> iterator() {
            return newEntryIterator();
        }
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<K,V> e = (Map.Entry<K,V>) o;
            Entry<K,V> candidate = getEntry(e.getKey());
            return candidate != null && candidate.equals(e);
        }
        public boolean remove(Object o) {
            return removeMapping(o) != null;
        }
        public int size() {
            return size;
        }
        public void clear() {
            HashMap.this.clear();
        }
    }

    /**
     * 做HashMap序列化时会调用此方法保存HashMap的状态和其中的数据
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws IOException
    {
        // Write out the threshold, loadfactor, and any hidden stuff
        s.defaultWriteObject();

        // Write out number of buckets
        if (table==EMPTY_TABLE) {
            s.writeInt(roundUpToPowerOf2(threshold));
        } else {
            s.writeInt(table.length);
        }

        // Write out size (number of Mappings)
        s.writeInt(size);

        // Write out keys and values (alternating)
        if (size > 0) {
            for(Map.Entry<K,V> e : entrySet0()) {
                s.writeObject(e.getKey());
                s.writeObject(e.getValue());
            }
        }
    }

    private static final long serialVersionUID = 362498820763181265L;

    /**
     * 反序列化时会调用此方法
     */
    private void readObject(java.io.ObjectInputStream s)
         throws IOException, ClassNotFoundException
    {
        // Read in the threshold (ignored), loadfactor, and any hidden stuff
        s.defaultReadObject();
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new InvalidObjectException("Illegal load factor: " +
                                               loadFactor);
        }

        // set other fields that need values
        table = (Entry<K,V>[]) EMPTY_TABLE;

        // Read in number of buckets
        s.readInt(); // ignored.

        // Read number of mappings
        int mappings = s.readInt();
        if (mappings < 0)
            throw new InvalidObjectException("Illegal mappings count: " +
                                               mappings);

        // capacity chosen by number of mappings and desired load (if >= 0.25)
        int capacity = (int) Math.min(
                    mappings * Math.min(1 / loadFactor, 4.0f),
                    // we have limits...
                    HashMap.MAXIMUM_CAPACITY);

        // allocate the bucket array;
        if (mappings > 0) {
            inflateTable(capacity);
        } else {
            threshold = capacity;
        }

        init();  // Give subclass a chance to do its thing.

        // Read the keys and values, and put the mappings in the HashMap
        for (int i = 0; i < mappings; i++) {
            K key = (K) s.readObject();
            V value = (V) s.readObject();
            putForCreate(key, value);
        }
    }

    // These methods are used when serializing HashSets
    int   capacity()     { return table.length; }
    float loadFactor()   { return loadFactor;   }
}
